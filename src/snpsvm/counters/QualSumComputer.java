package snpsvm.counters;

import java.util.Iterator;

import snpsvm.bamreading.AlignmentColumn;
import snpsvm.bamreading.FastaWindow;
import snpsvm.bamreading.MappedRead;

public class QualSumComputer extends VarCountComputer {
	
	final double maxScore = 500.0; //Maximum value for any base
	
	@Override
	public String getName() {
		return "quality.sums";
	}
	
	@Override
	public Double[] computeValue(FastaWindow window, AlignmentColumn col) {
		values[ref] = 0.0;
		values[alt] = 0.0;
		
		if (col.getDepth() > 0) {
			Iterator<MappedRead> it = col.getIterator();

			final char refBase = window.getBaseAt(col.getCurrentPosition()+1);
			while(it.hasNext()) {
				MappedRead read = it.next();
				if (read.hasBaseAtReferencePos(col.getCurrentPosition())) {
					byte b = read.getBaseAtReferencePos(col.getCurrentPosition());
					byte q = read.getQualityAtReferencePos(col.getCurrentPosition());
					int index = 0;
					if (b == 'N') 
						continue;
					if (b != refBase)
						index = 1;
					values[index] += q;
					if (values[index] > maxScore)
						values[index] = maxScore;
					
					
				}
			}
		}
		
		return values;
	}

}
