package com.aol.cyclops.streams.operators;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import lombok.Value;

import com.aol.cyclops.streams.StreamUtils;
@Value
public class BatchByTimeOperator<T> {
	Stream<T> stream;
	public Stream<List<T>> batchByTime(long time, TimeUnit t){
		Iterator<T> it = stream.iterator();
		long toRun = t.toNanos(time);
		return StreamUtils.stream(new Iterator<List<T>>(){
			long start = System.nanoTime();
			@Override
			public boolean hasNext() {
				return it.hasNext();
			}
			@Override
			public List<T> next() {
				
				List<T> list = new ArrayList<>();
				while(System.nanoTime()-start< toRun && it.hasNext()){
					list.add(it.next());
				}
				if(list.size()==0 && it.hasNext()) //time unit may be too small
					list.add(it.next());
				start = System.nanoTime();
				return list;
			}
			
		}).filter(l->l.size()>0);
	}
}
