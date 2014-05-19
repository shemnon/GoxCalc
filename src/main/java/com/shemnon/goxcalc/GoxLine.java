package com.shemnon.goxcalc;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.TimeZone;

/**
 * 
 * Created by shemnon on 16 May 2014.
 */
public class GoxLine {
    static final DateFormat DATE_FORMAT = new SimpleDateFormat("\"yyyy-MM-dd HH:mm:ss\"");
    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
    }
    
    String wallet;
    String entry;
    long date;
    String operation;
    long amountSatoshi;
    
    public GoxLine(String csv) throws ParseException {
        String[] values = csv.split(",");
        wallet = values[0];
        entry = values[1];
        date = DATE_FORMAT.parse(values[2]).toInstant().getEpochSecond();
        operation = values[3];
        amountSatoshi = Math.abs(new BigDecimal(values[4]).movePointRight(8).longValue());
    }
    
    public String toString() {
        return String.format("%s,%s,%s,%s,%s", 
                wallet,
                entry,
                DATE_FORMAT.format(new Date(date*1000)),
                operation,
                Double.toString(new BigDecimal(amountSatoshi).movePointLeft(8).doubleValue()));
    }
            
}
