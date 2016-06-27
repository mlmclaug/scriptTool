/*
Auth: mlm - 06/27/2016

CSV is a helper class to read/parse csv files.

*/
package edu.uvm.banner;
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVFormat
import static org.apache.commons.csv.CSVFormat.*

class CSV {
    String rowFmt = 'M'  // Format of each row read from the file M = map, L = list 
    // Default Parsing specification.
    CSVFormat rowParseFmt = DEFAULT.withHeader().withSkipHeaderRecord(true)

    CSV setParser( List ch = [], skipHeader = true){
        // Convenience method to set typical format options.
        //ch - optional list if column names used to name the column values in the map.
        //     if not supplied, the values in first row of the file will be used.
        //     
        //skipHeader - ignore first row (true|false}.
        rowParseFmt = DEFAULT.withHeader(ch.toArray(new String[0])).withSkipHeaderRecord(skipHeader)
        return this
    }

    List fetchAll(f, Closure processRow = null){
        // f is a File instance to be processed of a string containing the name of the file to process.
        // processRow - Closure to process each row, if file is too big to read into memory at once.
        //               Program is passed one row of data.

        List rows = []  // parsed data rows to be returned.  Empty if using processRow to process each row.

        File csvFile
        if ( f instanceof File ){
            csvFile = f
        }else{ // here is expected to be a valid file name
            csvFile = new File(f)
        }

        csvFile.withReader { reader ->
            CSVParser csv = new CSVParser(reader, rowParseFmt)
                    
            for (record in csv.iterator()) {

                def r
                if ( rowFmt == 'L'){
                    r = record.toList()
                }else{ //Map
                    r = record.toMap()
                }

                if (processRow) {
                    // Here processing Row by Row
                    processRow(r)
                } else {// Here accumulate and returning all rows to the caller
                    rows << r
                }
            }
            return rows
        }
    }
}
