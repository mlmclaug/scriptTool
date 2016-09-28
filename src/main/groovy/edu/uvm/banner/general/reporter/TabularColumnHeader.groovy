package edu.uvm.banner.general.reporter

class TabularColumnHeader {
	Integer width     // Max Number of characters in column
	String alignment  // L|C|R left|center|right justification (optional)
	String dataFmt    // printf format string for the data values (optional)
	String[] labels   // array of column header labels (one for each row) (optional)
	
	String fmtDataValue(def d){
		//return formated data value... truncate if overflows max width
		String cv = ""
		if ( 0 < dataFmt.size() ){
			try {
				cv = (d != null) ? sprintf(dataFmt,d ) : ""
			
			} catch (IllegalFormatConversionException e){
				// here typically have an int when expect a float or vice versa
			   cv = d
			   if (cv.isNumber()) {
				   // last ditch effort.. this will get many
				   cv = fmt_last_attempt(coerce_num(d) )
			   } 
			}
		} else {
			cv = (d != null) ? d : ""
		}
		cv = padCell(cv)
		return cv
	}
	def rowcount(){
		labels.size()
	}
	List fmtColLabels(Integer nrows){
		// returns formated column labels w/ 1 element per requested rows
		// justified to the bottom row
		List res = []
		
		if (nrows>labels.size()){
			//pad top rows w/ blanks
			(nrows-labels.size()).times({res += " ".multiply(width)})
		}
		//Now get the provided labels
		res += fmtLabelRows() 
		return res.flatten()
	}
		
	private String[] fmtLabelRows(){
		def res = []
		Integer nb = 0
		
		for (txt in labels){
			nb = Math.max(0,width - txt.size() )
			String l = padCell(txt)
			res << l
		}
		return res
	}
	
	private String padCell(String txt){
		Integer nb = Math.max(0,width - txt.size() )
		String l = ""
			if ( alignment == "R" ) {
				l = " ".multiply(nb) + txt
			} else if ( alignment == "C" ) {
			    String t = txt.trim()
				nb = Math.max(0,width - t.size() )
				def halfnb = nb/2
				Integer lnb = halfnb.setScale(0, java.math.RoundingMode.DOWN)
				Integer rnb =  halfnb.setScale(0, java.math.RoundingMode.UP)
				l = " ".multiply(lnb) + t + " ".multiply(rnb)
			} else { // default alignment == "L"
				l = txt + " ".multiply(nb)
			}
			
			if ( l.size() > width ){
				//too long-Truncate to Max width for column unless a number... 
				//if a number overflow w/ "*"
				if (l.isNumber()){
					l = "*" * width
				} else {
					l = l[0..width-1]
				}
			}
		return l
	}
	private def coerce_num( def n){
		// coerce to datatype expected by the format expression..
		// f to d or vice versa... kinda a last ditch effort
		boolean isf = dataFmt.contains('f') 
		boolean isd = dataFmt.contains('d') 
		boolean iss = dataFmt.contains('s') 
		if ( 0<dataFmt.size() && (isf || isd || iss)  ){
		  if (isf ) {
			  // expect a float
			  n = (float) n
		  } else if (isd ) {
			  // expect a double
			  n = (double) n
		  } else if (iss ) {
			  // expect a string
		      String s = n
			  n = s
		  }
		}
		 return n
		}
	
	private String fmt_last_attempt(def d){
		// one last time after coercing the data type... 
		String cv
		try {
			cv = d ? sprintf(dataFmt,d ) : ""
		} catch (IllegalFormatConversionException e){
		   cv = d
		   }
		return cv
	}
	
}
