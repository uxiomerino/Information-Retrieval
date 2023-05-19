package es.udc.fi.irdatos.c2122.cord;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MAPevaluation {

    private String relevanceJudgmentsFile;
    private String resultsFolder;

    public MAPevaluation(String relevanceJudgmentsFile, String resultsFolder) {
        this.relevanceJudgmentsFile = relevanceJudgmentsFile;
        this.resultsFolder = resultsFolder;
    }

    public void evaluate(Integer n) throws IOException {
        Map<String, List<String>> relevanceJudgments = readRelevanceJudgments();
        Map<String, List<String>> allResults = new HashMap<>();
        File folder = new File(resultsFolder);
        File[] resultFiles = folder.listFiles();
        for (File file : resultFiles) {
            if (file.isFile()) {
                Map<String, List<String>> results = readResults(file.getPath());
                for (String query : results.keySet()) {
                    if (!allResults.containsKey(query)) {
                        allResults.put(query, new ArrayList<>());
                    }
                    allResults.get(query).addAll(results.get(query));
                }
            }
        }
        double totalMAP = 0.0;
        int queryCount = 0;
        for (String query : relevanceJudgments.keySet()) {
            List<String> judgments = relevanceJudgments.get(query);
            List<String> queryResults = allResults.get(query);
            double queryMAP = calculateMAP(judgments, queryResults, n);
            System.out.println("MAP for query '" + query + "': " + queryMAP);
            totalMAP += queryMAP;
            queryCount++;
        }
        double avgMAP = totalMAP / queryCount;
        System.out.println("Average MAP: " + avgMAP);
    }

    private Map<String, List<String>> readRelevanceJudgments() throws IOException {
        Map<String, List<String>> relevanceJudgments = new HashMap<>();
        BufferedReader reader = new BufferedReader(new FileReader(relevanceJudgmentsFile));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("\\s+");
            String query = parts[0];
            String documentId = parts[2];
            int relevance = Integer.parseInt(parts[3]);
            if (relevance > 0) {
                if (!relevanceJudgments.containsKey(query)) {
                    relevanceJudgments.put(query, new ArrayList<>());
                }
                relevanceJudgments.get(query).add(documentId);
            }
        }
        reader.close();
        return relevanceJudgments;
    }

    private Map<String, List<String>> readResults(String resultsFile) throws IOException {
        Map<String, List<String>> results = new HashMap<>();
        BufferedReader reader = new BufferedReader(new FileReader(resultsFile));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("\\s+");
            String query = parts[0];
            String documentId = parts[2];
            if (!results.containsKey(query)) {
                results.put(query, new ArrayList<>());
            }
            results.get(query).add(documentId);
        }
        reader.close();
        return results;
    }

    private double calculateMAP(List<String> judgments, List<String> results, Integer n) {
        int relevantCount = 0;
        double totalPrecision = 0.0;
        for (int i = 0; i < results.size(); i++) {
            String documentId = results.get(i);
            if (judgments.contains(documentId)) {
                relevantCount++;
                double precision = (double) relevantCount / (i + 1);
                totalPrecision += precision;
            }
        }
        if (relevantCount == 0) {
            return 0.0;
        } else {
            return totalPrecision / n;
        }
    }

    public static void main(String[] args) throws IOException {
        if (Arrays.asList(args).contains("-h")) {
            System.out.println("Para obtener los índices de la colección CORD-19, ejecutar el programa 'ParserIndexer.java' con los siguientes argumentos:");
            System.out.println("1. Path a la carpeta en la que se van a almacenar los índices");
            System.out.println("En caso de no indicar nada usará el path por defecto (src/main/resources/).");
            System.out.println("\n");
            System.out.println("Para buscar en los índices de la colección CORD-19 y obtener los documentos más relevantes, ejecutar el programa 'IndexSearch.java' con los siguientes argumentos:");
            System.out.println("1. Path a la carpeta en la que se encuentran los índices");
            System.out.println("2. Método de búsqueda a utilizar (\"1\", \"2\" o \"3\")");
            System.out.println("3. Número de documentos a mostrar (\"10\", \"100\" o \"1000\")");
            System.out.println("\n");
            System.out.println("Para evaluar los resultados, ejecutar el programa 'MAPevaluation.java' con los siguientes argumentos:");
            System.out.println("1. Número de documentos sobre los que se va a calcular el MAP (\"10\", \"100\" o \"1000\"), que tiene que coincidir con el número de documentos indicado en el programa 'IndexSearch.java', excepto cuando se trate del método 3 que tendrá que recibir n/10");
            System.exit(0);
        }

        Integer n;
        try {
            n = Integer.parseInt(args[0]);
        } catch (Exception e) {
            System.err.println("Error reading n: " + args[0]);
            e.printStackTrace();
            return;
        }
        String relevanceJudgmentsFile = "relevant_judgments.txt";
        String resultsFolder = "results";
        MAPevaluation evaluator = new MAPevaluation(relevanceJudgmentsFile, resultsFolder);
        evaluator.evaluate(n);
    }
}
