package snpsvm.bamreading;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import snpsvm.app.SNPCaller;
import snpsvm.bamreading.IntervalList.Interval;
import snpsvm.counters.ColumnComputer;

/**
 * Splits a set of intervals in half, calls snps concurrently on each half, then merges the results 
 * @author brendanofallon
 *
 */
public class SplitSNPAndCall implements HasBaseProgress {
	
	//Interval sets smaller than this size will be computed forthwith, otherwise
	//they'll be split into approximate halves and each side will be processed separately
	int thresholdExtent = 250000; 
	
	protected File reference;
	protected File inputBam;
	protected BAMWindowStore bamWindows;
	protected File model;
	private List<ColumnComputer> counters;
	private ThreadPoolExecutor pool;
	private List<SNPCaller> callers = new ArrayList<SNPCaller>();
	
	public SplitSNPAndCall(File referenceFile, BAMWindowStore bamWindows, File modelFile, List<ColumnComputer> counters, ThreadPoolExecutor pool) {
		this.reference = referenceFile;
		this.bamWindows = bamWindows; 
		this.model = modelFile;
		this.counters = counters;
		this.pool = pool;	
	}

	/**
	 * Recursive function that will call all snps in the interval list. If the interval list is
	 * small enough then the snps just be submitted to the thread pool, if not the intervals will be split
	 * in half and each half will be submitted 
	 * @param intervals
	 */
	public void submitAll(IntervalList intervals) {
			
		if (intervals.getExtent() < thresholdExtent) {
			//Intervals size is pretty small, just call 'em
			SNPCaller caller = new SNPCaller(reference, model, intervals, counters, bamWindows);
			callers.add(caller);
			//pool.submit(caller);
			pool.execute(caller);
			//System.out.println("Submitting job, " + callers.size() + " tot jobs, queue has :" + pool.getActiveCount() + " active and " + pool.getCompletedTaskCount() + " completed jobs");
		}
		else {	
			//Intervals cover a lot of ground, so split them in half and submit each half
			IntervalList[] intervalArray = splitIntervals(intervals);
			submitAll(intervalArray[0]);
			submitAll(intervalArray[1]);
		}
	}

	public int getBasesCalled() {
		int tot = 0;
		for(SNPCaller caller : callers) {
			tot += caller.getBasesCalled();
		}
		return tot;
	}
	
	/**
	 * The number of 'caller' jobs submitted by this object
	 * @return
	 */
	public int getCallerCount() {
		return callers.size();
	}
	
	/**
	 * Shut down the variant pool and wait until all jobs are done. Then get all variants
	 * and return them. 
	 * @return
	 */
	public List<Variant> getAllVariants() {
		
		pool.shutdown();
		try {
			pool.awaitTermination(100, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		List<Variant> vars = new ArrayList<Variant>();
		for(SNPCaller caller : callers) {
			List<Variant> subVars = caller.getVariantList();
			if (subVars != null)
			vars.addAll( subVars );
		}
		return vars;
	}

	
	protected static IntervalList[] splitIntervals(IntervalList intervals) {
		IntervalList[] subIntervals = new IntervalList[2];
		subIntervals[0] = new IntervalList();
		subIntervals[1] = new IntervalList();
		
		//If only one interval, split it in half
		if (intervals.getIntervalCount() == 1) {
			List<Interval> intList = intervals.asList();
			String contig = intervals.getContigs().iterator().next();
			
			Interval interval = intList.get(0);
			int midPoint = (interval.getFirstPos() + interval.getLastPos())/2;
			subIntervals[0].addInterval(contig, interval.getFirstPos(), midPoint);
			subIntervals[1].addInterval(contig, midPoint, interval.getLastPos());
			return subIntervals;
		}
		
		if (intervals.getIntervalCount() == 2) {
			List<Interval> intList = intervals.asList();
			
			Iterator<String> contigIt = intervals.getContigs().iterator();
			
			String contig0 = contigIt.next();
			String contig1 = contigIt.next();
			
			Interval interval = intList.get(0);
			subIntervals[0].addInterval(contig0, interval);
			
			interval = intList.get(1);
			subIntervals[1].addInterval(contig1, interval);
			return subIntervals;
		}
		
		int fullSize = intervals.getExtent();
		int extentSoFar = 0;
		int index = 0;
		for(String contig : intervals.getContigs()) {
			for(Interval interval : intervals.getIntervalsInContig(contig)) {
				if (extentSoFar > fullSize / 2) {
					index = 1;
				}
				subIntervals[ index ].addInterval(contig, interval);
				extentSoFar += interval.lastPos - interval.firstPos;
			}
		}
		return subIntervals;
	}
}
