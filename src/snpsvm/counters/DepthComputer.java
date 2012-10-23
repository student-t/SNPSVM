package snpsvm.counters;

import snpsvm.bamreading.AlignmentColumn;
import snpsvm.bamreading.FastaWindow;

/**
 * Computes total read depth at the current position
 * @author brendan
 *
 */
public class DepthComputer implements ColumnComputer {

	private final Double[] value = new Double[1];
	
	@Override
	public String getName() {
		return "total.depth";
	}
	
	@Override
	public Double[] computeValue(final char refBase, FastaWindow window, AlignmentColumn col) {
		value[0] = (double)col.getDepth();
		return value;
	}

	@Override
	public int getColumnCount() {
		return value.length;
	}


	@Override
	public String getColumnDesc(int which) {
		return "Total read depth at site";
	}
}
