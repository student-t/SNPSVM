package snpsvm.bamreading;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import snpsvm.bamreading.FastaIndex.IndexNotFoundException;
import snpsvm.bamreading.FastaReader2.EndOfContigException;
import snpsvm.counters.BinomProbComputer;
import snpsvm.counters.ColumnComputer;

public class ReferenceBAMEmitter {

	final FastaWindow refReader;
	protected AlignmentColumn alnCol;
	private Map<String, Integer> contigMap;
	List<ColumnComputer> counters;
	protected BufferedWriter positionWriter = null;
	protected DecimalFormat formatter = new DecimalFormat("0.0####");
	protected BinomProbComputer binomComputer = new BinomProbComputer(); //Used for initial filtering 
	protected final int minDepth;
	protected final int minVarDepth;
	
	
	public ReferenceBAMEmitter(File reference, List<ColumnComputer> counters, BamWindow window, CallingOptions ops) throws IOException, IndexNotFoundException {
		refReader = new FastaWindow(reference);
		contigMap = new HashMap<String, Integer>();
		for(String contig : refReader.getContigs()) {
			contigMap.put(contig, refReader.getContigLength(contig).intValue());
		}
		alnCol = new AlignmentColumn(window);
		this.minDepth = ops.getMinTotalDepth();
		this.minVarDepth = ops.getMinVariantDepth();
		this.counters = counters;
	}
	
	public ReferenceBAMEmitter(File reference, File bamFile, List<ColumnComputer> counters, CallingOptions ops) throws IOException, IndexNotFoundException {
		refReader = new FastaWindow(reference);
		//contigMap = refReader.getContigSizes();
		alnCol = new AlignmentColumn(bamFile);
		this.minDepth = ops.getMinTotalDepth();
		this.minVarDepth = ops.getMinVariantDepth();
		this.counters = counters;
	}
	
	/**
	 * If non-null, positions of each site will be emitted to this file, which helps to resolve 
	 * where variant calls are. 
	 * @param file
	 * @throws IOException 
	 */
	public void setPositionsWriter(BufferedWriter writer) throws IOException {
		positionWriter = writer;
	}
	
	public void emitLine(PrintStream out) {
		
		if (alnCol.getApproxDepth() >= minDepth) {
            final char refBase = refReader.getBaseAt(alnCol.getCurrentPosition());
            if (refBase == 'N') {
            	return;
            }
            
            boolean differringBases = alnCol.hasXDifferingBases(refBase, minVarDepth);

            if (! differringBases) {
               return;
            }

			//System.out.println(alnCol.getCurrentPosition() + "\t" + refBase + " : " + alnCol.getBasesAsString());
			out.print("-1"); //libsvm requires some label here but doesn't use it
			int index = 1;
			for(ColumnComputer counter : counters) {
				final double[] values = counter.computeValue(refBase, refReader, alnCol);
				for(int i=0; i<values.length; i++) {
					if (values[i] < -1.0 || values[i] > 1.0) {
						//final double x = values[i];
						throw new IllegalArgumentException("Invalid value for counter: " + counter.getName() + " found value=" + values[i] + " chr: " + alnCol.getCurrentContig() + " pos:" + alnCol.getCurrentPosition());
					}
					if (Double.isInfinite(values[i]) || Double.isNaN(values[i])) {
						throw new IllegalArgumentException("Non-regular value for counter: " + counter.getName() + " found value=" + values[i]);
					}
					if (values[i] != 0)
						out.print("\t" + index + ":" + formatter.format(values[i]) );
					index++;
				}
			}
			out.println();
			
			if (positionWriter != null) {
				try {
					int[] counts = alnCol.getBaseCounts();
					positionWriter.write( alnCol.getCurrentContig() + ":" + alnCol.getCurrentPosition() + ":" + refBase + ":" + counts[AlignmentColumn.A] + "," + counts[AlignmentColumn.C] + "," + counts[AlignmentColumn.G] + "," + counts[AlignmentColumn.T] + "\n");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Emit all information for all contigs
	 * @throws IOException 
	 */
	public void emitAll(PrintStream out) throws IOException {
		for(String contig : contigMap.keySet()) {
			emitContig(contig, out);
		}
	}
	
	public void emitContig(String contig) throws IOException {
		emitContig(contig, System.out);
	}
	
	/**
	 * Emit all information for given contig
	 * @param contig
	 * @throws IOException 
	 */
	public void emitContig(String contig, PrintStream out) throws IOException {
		Integer size = contigMap.get(contig);
		emitWindow(contig, 1, size, out);
	}
	
	public void emitWindow(String contig, int start, int end) throws IOException {
		emitWindow(contig, start, end, System.out);
	}
	
	public void emitWindow(String contig, int start, int end, PrintStream out) throws IOException {
		if (! refReader.containsContig(contig)) {
			//throw new IllegalArgumentException("Reference does not have contig : " + contig);
			System.err.println("Warning, reference does not contain contig: " + contig + ".  Skipping it.");
			return;
		}
		
		if (! alnCol.containContig(contig)) {
			System.err.println("Warning, alignment does not contain contig: " + contig + ".  Skipping it.");
			return;
		}
		
		try {
			refReader.resetTo(contig, Math.max(1, start-refReader.windowSize/2));
			alnCol.advanceTo(contig, start);

			int curPos = start;
			while(curPos < end && alnCol.hasMoreReadsInCurrentContig()) {
				emitLine(out);

				if (refReader.indexOfLeftEdge()<(alnCol.getCurrentPosition()-refReader.windowSize/2)) {
					try {
						refReader.shift();
					}
					catch(EndOfContigException ex) {
						//don't worry about it
					}
				}
				alnCol.advance(1);
				curPos++;

				//Sanity check
				if (alnCol.getCurrentPosition() != (curPos)) {
					System.err.println("Yikes, bam reader position is not equal to current position");
				}
			}

		} catch (EndOfContigException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
	}
	
}
