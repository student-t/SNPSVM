package snpsvm.counters;

import java.util.Iterator;

import snpsvm.bamreading.AlignmentColumn;
import snpsvm.bamreading.FastaWindow;
import snpsvm.bamreading.MappedRead;

public class StrandBiasComputer implements ColumnComputer {

	final Double[] value = new Double[1];
	final Double[] forward = new Double[2];
	final Double[] reverse = new Double[2];
	
	@Override
	public String getName() {
		return "strand.bias";
	}

	@Override
	public Double[] computeValue(final char refBase, FastaWindow window, AlignmentColumn col) {
		value[0] = 0.0;
		forward[0] = 1.0; //prevents divide by zero errors
		forward[1] = 1.0;
		reverse[0] = 1.0;
		reverse[1] = 1.0;
		
		if (col.getDepth() > 0) {
			Iterator<MappedRead> it = col.getIterator();
			while(it.hasNext()) {
				MappedRead read = it.next();
				if (read.hasBaseAtReferencePos(col.getCurrentPosition())) {
					byte b = read.getBaseAtReferencePos(col.getCurrentPosition());
					if (b == 'N') 
						continue;
					
					int index = 0;
					if ( b != refBase)
						index = 1;
					
					if (read.getRecord().getFirstOfPairFlag())
						forward[index]++;
					else 
						reverse[index]++;
					
				}
			}
		}
		
		value[0] = (forward[1]/reverse[1] - 0.5)*(forward[1]/reverse[1] - 0.5) / 0.5;
		value[0] += (forward[0]/reverse[0] - 0.5)*(forward[0]/reverse[0] - 0.5) / 0.5;
		
		if (value[0] > 100.0)
			value[0] = 100.0;
		return value;
	}

}
