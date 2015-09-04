package com.aol.cyclops.streams.operators;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import lombok.Value;

import com.aol.cyclops.sequence.streamable.Streamable;
import com.aol.cyclops.streams.StreamUtils;

@Value
public class WindowStatefullyWhileOperator<T> {
	 private static final Object UNSET = new Object();
	Stream<T> stream;
	public Stream<Streamable<T>> windowStatefullyWhile(BiPredicate<Streamable<T>,T> predicate){
		Iterator<T> it = stream.iterator();
		return StreamUtils.stream(new Iterator<Streamable<T>>(){
			Streamable<T> last= Streamable.empty();
			T value = (T)UNSET;
			@Override
			public boolean hasNext() {
				return value!=UNSET || it.hasNext();
			}
			@Override
			public Streamable<T> next() {
				
				List<T> list = new ArrayList<>();
				if(value!=UNSET)
					list.add(value);
				
				while(list.size()==0&& it.hasNext()){
					while(it.hasNext() && predicate.test(last,value=it.next())){
						list.add(value);
						value=(T)UNSET;
					}
				}
				return last = Streamable.fromIterable(list);
			}
			
		});
	}
}
