package com.aol.cyclops2.internal.stream.spliterators.push;

import com.aol.cyclops2.types.mixins.Printable;
import cyclops.async.Queue;
import cyclops.box.Mutable;
import cyclops.stream.ReactiveSeq;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

/**
 * Created by johnmcclean on 12/01/2017.
 */
public class PublisherFlatMapOperator<T,R> extends BaseOperator<T,R> implements Printable {


    int maxCapacity=256;
    final Function<? super T, ? extends Publisher<? extends R>> mapper;;

    public PublisherFlatMapOperator(Operator<T> source, Function<? super T, ? extends Publisher<? extends R>> mapper){
        super(source);
        this.mapper = mapper;




    }

    AtomicInteger active = new AtomicInteger(0);


    @Override
    public StreamSubscription subscribe(Consumer<? super R> onNext, Consumer<? super Throwable> onError, Runnable onComplete) {

        AtomicReference<Subscription> activeSub = new AtomicReference<>(null);
        StreamSubscription[] s = {null} ;

        AtomicInteger status = new AtomicInteger(0); //1st bit for completing, 2 bit for inner active, 100 for complete



        StreamSubscription res = new StreamSubscription(){
            LongConsumer work = n-> {
                System.out.println("New demand! Requesting on thread " + Thread.currentThread().getId() + " demand "  + this.requested.get());

                if((status.get() &2)==0){ //inner not active
                    System.out.println("Outer request to parent");
                    s[0].request(1);
                }else{
                    System.out.println("Outer request to inner");
                    activeSub.get().request(1);
                }
            };
            @Override
            public void request(long n) {
                if(n<=0)
                    onError.accept(new IllegalArgumentException( "3.9 While the Subscription is not cancelled, Subscription.request(long n) MUST throw a java.lang.IllegalArgumentException if the argument is <= 0."));

                this.singleActiveRequest(n,work);

            }

            @Override
            public void cancel() {
                s[0].cancel();
                super.cancel();
            }
        };
        s[0] = source.subscribe(e-> {
                    try {

                        Publisher<? extends R> split = mapper.apply(e);


                        PublisherToOperator<R> op = new PublisherToOperator<>((Publisher<R>)split);
                        Subscription sLocal = op.subscribe(el->{
                            onNext.accept(el);
                            res.requested.decrementAndGet();
                            if(res.isActive()) {
                                System.out.println("Inner requesting more!");
                                activeSub.get().request(1);
                            }
                        },onError,()->{
                            System.out.println("Inner complete!");
                            int thunkStatusLocal = -1;
                            do {
                                thunkStatusLocal = status.get();


                            }
                            while (!status.compareAndSet(thunkStatusLocal, thunkStatusLocal & ~(1 << 1))); //unset inner active

                            if (status.compareAndSet(1, 100)) {
                                onComplete.run();
                                return;
                            }else{
                                if(res.isActive()){
                                    s[0].request(1l);
                                }
                            }
                        });


                        int statusLocal =-1;
                        do {
                            statusLocal = status.get();


                        }while(!status.compareAndSet(statusLocal,statusLocal | (1 << 1))); //set inner active
                        if(res.isActive()){
                            sLocal.request(1l);
                        }

                        activeSub.set(sLocal); //set after active



                    } catch (Throwable t) {

                        onError.accept(t);
                    }
                }
                ,t->{
                    onError.accept(t);
                    res.requested.decrementAndGet();
                    if(res.isActive()){
                        s[0].request(1);
                    }
                },()->{
                    int statusLocal = -1;
                    do {
                        statusLocal = status.get();


                    }while(!status.compareAndSet(statusLocal,statusLocal | (1 << 0)));

                    if(status.compareAndSet(1,100)){
                        System.out.println("Outer complete!");
                        onComplete.run();
                    }

                });

        return res;
    }

    @Override
    public void subscribeAll(Consumer<? super R> onNext, Consumer<? super Throwable> onError, Runnable onCompleteDs) {
        Deque<Publisher<? extends R>> queued = new ConcurrentLinkedDeque<>();
        ManyToOneConcurrentArrayQueue<R> data = new ManyToOneConcurrentArrayQueue<R>(256);
        ManyToOneConcurrentArrayQueue<Throwable> errors = new ManyToOneConcurrentArrayQueue<>(256);
        AtomicReference<PVector<Subscription>> activeSubs = new AtomicReference<>(TreePVector.empty());
        source.subscribeAll(e-> {
                    try {

                        Publisher<? extends R> next = mapper.apply(e);
                        queued.add(next);
                        StreamSubscription sub = new StreamSubscription();
                        sub.request(maxCapacity);
                        drainAndLaunch(sub,onNext, queued, data, errors);

                    } catch (Throwable t) {

                        onError.accept(t);
                    }
                }
                ,onError,()->{

                    while(active.get()>0 || data.size()>0 || queued.size()>0 || errors.size()>0){
                        //drain, create demand, launch queued publishers
                        Object next = data.poll();
                        if(next!=null)
                            onNext.accept(print(nilsafe(next)));

                        if(errors.size()>0){
                                onError.accept(errors.poll());
                        }
                    }
                    System.out.println("Completing!! " + queued.size() + "  " + active.get());
                    onCompleteDs.run();
                });
    }

    private <T> T nilsafe(Object o){
        if(Queue.NILL==o){
            return null;
        }
        return (T)o;
    }

    private void drainAndLaunch(StreamSubscription maxCapacity,Consumer<? super R> onNext, Deque<Publisher<? extends R>> queued, ManyToOneConcurrentArrayQueue<R> data, ManyToOneConcurrentArrayQueue<Throwable> errors) {

        while(queued.size()>0 && active.get()<10) {
            subscribe(queued, data, errors);
            Object next = data.poll();
            if(next!=null)
                onNext.accept(print(nilsafe(next)));


        }



    }

    private void subscribe(Deque<Publisher<? extends R>> queued,
                           ManyToOneConcurrentArrayQueue<R> data,
                           ManyToOneConcurrentArrayQueue<Throwable> errors){


        Publisher<? extends R> next = queued.poll();
        if(next==null)
            return;
        next.subscribe(new Subscriber<R>() {
            AtomicReference<Subscription> sub;

            private Object nilsafe(Object o){
                if(o==null)
                    return Queue.NILL;
                return o;
            }

            @Override
            public void onSubscribe(Subscription s) {

                this.sub=new AtomicReference<>(s);
                active.incrementAndGet();
                s.request(Long.MAX_VALUE);

            }

            @Override
            public void onNext(R r) {
                //Optimization check if this is the
                //main thread and if so just call onNext
                data.offer((R)nilsafe(r));
               // sub.get().request(1l);

            }

            @Override
            public void onError(Throwable t) {
                //Optimization check if this is the
                //main thread and if so just call onError
                errors.offer(t);
             //   sub.get().request(1l);
            }

            @Override
            public void onComplete() {


                if(queued.size()>0){
                    subscribe(queued,data,errors);
                }
                active.decrementAndGet();
            }
        });
    }

}
