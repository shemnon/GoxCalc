package com.shemnon.goxcalc;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 
 * Created by shemnon on 16 May 2014.
 */
public class Main {
    
    public static void main(String... args) {
        // fist we get all the gox CSV values in memory
        List<GoxLine> goxLines = createGoxLines();
        System.out.println(goxLines.size());

        Map<Long, List<GoxLine>> byAmount = goxLines.stream()
                .collect(Collectors.groupingByConcurrent(gl -> gl.amountSatoshi));
        
        int start;
        int end;
        int increment;
        
        if (args.length > 0) {
            start = Integer.parseInt(args[0]);
        } else {
            start = 277995;
        }
        if (args.length > 1) {
            end = Integer.parseInt(args[1]);
        } else {
            end = start - 144;
        }
        if (args.length > 2) {
            increment = Integer.parseInt(args[2]);
        } else {
            increment = 144;
        }
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }

        String base = "./goxcalc-";
        if (args.length > 3) {
            base = args[3];
        }

        for (int blockstart = start; blockstart < end; blockstart += increment)
            try (
                FileOutputStream fos = new FileOutputStream(base + blockstart + "-" + (blockstart + increment - 1) + ".csv");
                PrintStream ps = new PrintStream(fos)
            ) {
                for (int i = blockstart; i < blockstart + increment; i++) {
                    examineBlock(i, byAmount, ps);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    static final String BLOCK_BASE = "http://mainnet.helloblock.io/v1/blocks/";
    static final int delta = 10;
    static final long day_in_seconds = 60*60*24;
    static final String TX_INTERVAL = "/transactions?limit=" + delta + "&offset=";
    
    private static void examineBlock(int blockNum, Map<Long, List<GoxLine>> byAmount, PrintStream out) {
        try (InputStream blockstream = new URL(BLOCK_BASE + blockNum).openStream();
             JsonReader blockreader = Json.createReader(blockstream)
        ) {
            JsonObject result = blockreader.readObject();
            JsonObject data = result.getJsonObject("data");
            JsonObject block = data.getJsonObject("block");
            int numTX = block.getInt("txsCount");
            
            for (int i = 0; i < numTX; i += delta) {
                try (InputStream txstream = new URL(BLOCK_BASE + blockNum + TX_INTERVAL + i).openStream();
                     JsonReader txreader = Json.createReader(txstream)
                ) {
                    JsonObject resulttx = txreader.readObject();
                    JsonObject txdata = resulttx.getJsonObject("data");
                    JsonArray transactions = txdata.getJsonArray("transactions");
                    transactions.forEach(jv -> {
                        JsonObject tx = ((JsonObject)jv);
                        String txid = tx.getString("txHash");
                        long blocktime = tx.getJsonNumber("blockTime").longValue();
                        JsonArray outputs = tx.getJsonArray("outputs");
                        outputs.forEach(jv2 -> {
                            JsonObject coin = ((JsonObject)jv2);
                            long value = coin.getJsonNumber("value").longValue();
                            List<GoxLine> lines = byAmount.get(value);
                            if (lines != null && lines.size() < 11) {
                                // to reduce noise, go for only bins of 10 or less
                                lines.stream()
                                    .filter(gl -> (gl.date < blocktime + day_in_seconds) && (gl.date > blocktime))
                                    .forEach(gl -> out.println(
                                        gl.toString() + "," + 
                                        txid + "," + 
                                        coin.getInt("index") + "," + 
                                        coin.getString("nextTxHash", "-") + "," + 
                                        coin.getInt("nextTxinIndex", -1) + "," + 
                                        coin.getString("address") + "," + 
                                        blockNum)
                                    );
                            }
                            
                        });
                    });
                }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<GoxLine> createGoxLines() {
        List<GoxLine> goxLines = new ArrayList<>(4_000_000);
        for (int i = 1; i < 10; i++) {
            InputStream is = Main.class.getResourceAsStream("/btc_xfer_report-" + i + ".csv");
            Reader reader = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(reader);

            br.lines()
                .map(s -> {
                    try {
                        return new GoxLine(s);
                    } catch (Exception e) {
                        System.out.println(e.toString());
                        return null;
                    }
                })
                .filter(f -> f != null)
                .sorted((b, a) -> {
                    if (a.date == b.date) {
                        if (a.amountSatoshi == b.amountSatoshi) {
                            return a.entry.compareTo(b.entry);
                        } else {
                            return Long.compare(a.amountSatoshi, b.amountSatoshi);
                        }
                    } else {
                        return Long.compare(a.date, b.date);
                    }
                })
                .forEach(goxLines::add);
        }
        return goxLines;
    }
}
