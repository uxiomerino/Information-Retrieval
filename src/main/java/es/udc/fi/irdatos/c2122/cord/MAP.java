package es.udc.fi.irdatos.c2122.cord;

import java.util.List;

public class MAP {
    public static double calculateMAP(List<Result> results) {
        int numRelevant = 0;
        double sumPrecision = 0.0;

        for (int i = 0; i < results.size(); i++) {
            Result result = results.get(i);
            if (result.isRelevant()) {
                numRelevant++;
                double precision = (double) numRelevant / (i + 1);
                sumPrecision += precision;
            }
        }

        int numRetrieved = results.size();
        return (1.0 / numRetrieved) * sumPrecision;
    }
}

class Result {
    private String document;
    private boolean relevant;

    public Result(String document, boolean relevant) {
        this.document = document;
        this.relevant = relevant;
    }

    public String getDocument() {
        return document;
    }

    public boolean isRelevant() {
        return relevant;
    }
}
