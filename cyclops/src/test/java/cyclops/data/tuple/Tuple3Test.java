package cyclops.data.tuple;

import cyclops.companion.Monoids;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Created by johnmcclean on 04/10/2017.
 */
public class Tuple3Test {
    Tuple3<Integer,Integer,Integer> tuple;
    Tuple3<String,Integer,Integer> lazyT1;
    @Before
    public void setUp() throws Exception {
        tuple = Tuple.tuple(2,5,10);
        lazyT1 = Tuple.lazy(()->{
         called++;
          return "hello";
        },()->1,()->2);
        called=  0;

    }

    @Test
    public void of() throws Exception {
        assertThat(Tuple3.of(2,5,10),equalTo(tuple));
    }

    int called;
    @Test
    public void lazy() throws Exception {

        assertThat(called,equalTo(0));
        assertThat(lazyT1._1(),equalTo("hello"));
        assertThat(called++,equalTo(1));
    }

    @Test
    public void _1() throws Exception {

        assertThat(tuple._1(),equalTo(2));
    }

    @Test
    public void _2() throws Exception {

        assertThat(tuple._2(),equalTo(5));
    }

    @Test
    public void _3() throws Exception {

        assertThat(tuple._3(),equalTo(10));
    }






    @Test
    public void map() throws Exception {
        assertThat(tuple.mapAll(i->i+1, i->i+1,i->i+1),equalTo(Tuple.tuple(3,6,11)));
    }




    @Test
    public void eager() throws Exception {
        assertThat(Tuple.lazy(()->"new",()->"boo!",()->"hello").eager(),equalTo(Tuple.tuple("new","boo!","hello")));
    }

    @Test
    public void memo(){
        Tuple3<String,Integer,Integer> lazy = Tuple.lazy(()->{
            called++;
            return "lazy";
        },()->10,()->5);
        lazy._1();
        lazy._1();
        assertThat(called,equalTo(2));
        called= 0;
        Tuple3<String,Integer,Integer> memo = lazy.memo();
        memo._1();
        memo._1();
        assertThat(called,equalTo(1));
    }


    @Test
    public void lazyMap() throws Exception {
        assertThat(tuple.lazyMapAll(i->i+1, i->i+1,i->i+1),equalTo(Tuple.tuple(3,6,11)));
        tuple.lazyMapAll(i->{
            called++;
            return i+1;
        },i->i,i->i+1);
        assertThat(called,equalTo(0));
    }


    @Test
    public void flatMap() throws Exception {
        assertThat( tuple.flatMap(Monoids.intSum,Monoids.intSum, i->Tuple.tuple(5,2,i+1)),equalTo(Tuple.tuple(7,7,11)));
    }



    @Test
    public void visit() throws Exception {
        assertThat(tuple.visit((a, b,c)->a+b+c),equalTo(17));
    }

    @Test
    public void testToString() throws Exception {
        assertThat(tuple.toString(),equalTo("[2,5,10]"));
    }


    @Test
    public void transform(){
       assertThat(lazyT1.transform((a,b,c)->a+b+c),equalTo("hello12"));
    }



}
