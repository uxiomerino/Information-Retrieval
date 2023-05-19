package es.udc.fi.irdatos.c2122.cord;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import org.apache.lucene.document.KnnVectorField;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;

import static es.udc.fi.irdatos.c2122.util.ObjectReaderUtils.*;

public class ParserIndexer {
    private static final Path DEFAULT_COLLECTION_PATH = Paths.get("src", "main", "resources");
    private static final Path collectionPathJSON = Paths.get("src", "main", "resources", "document_parses",
            "pdf_json");
    private static String METADATA_FILE_NAME = "metadata.csv";
    private static final ObjectReader JSON_READER = JsonMapper.builder().findAndAddModules().build()
            .readerFor(ReadJSON.class);

    /**
     * Utility method to read a json from a file to a String
     *
     * @param jsonPath path of the json file
     * @return a String with the text of the json
     */
    private static final HashMap<String, String> readJSONpath(Path jsonPath) {
        ReadJSON json;
        try {
            json = JSON_READER.readValue(jsonPath.toFile());
        } catch (IOException e) {
            System.err.println("Error reading json file: " + jsonPath);
            e.printStackTrace();
            return null;
        }

        HashMap<String, String> dict = new HashMap<>();
        dict.put("Introduction", "");
        dict.put("Others", "");
        if (json.body_text() != null) {
            for (ReadJSON.BodyText body_text : json.body_text()) {
                if (body_text.section().equals("Introduction")) {
                    String oldvalue = dict.get("Introduction");
                    dict.put("Introduction", oldvalue + body_text.text());
                }
            }
            if (dict.get("Introduction").equals("")) {
                for (ReadJSON.BodyText body_text : json.body_text()) {
                    String oldvalue = dict.get("Others");
                    dict.put("Others", oldvalue + body_text.text());
                }
            }
        }
        return dict;
    }

    public static void main(String[] args) {
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

        Path collectionPath;
        if (args.length > 1) {
            collectionPath = Paths.get(args[1]);
        } else {
            collectionPath = DEFAULT_COLLECTION_PATH;
        }

        Path CordPath = collectionPath.resolve(METADATA_FILE_NAME);
        CsvSchema schema = CsvSchema.emptySchema().withHeader().withArrayElementSeparator(", ");
        ObjectReader reader = new CsvMapper().readerFor(Metadata.class).with(schema);

        Path CordPathEmbedding = collectionPath.resolve("cord_19_embeddings_2020-07-16.csv");
        Map<String, float[]> embeddingsMap = new HashMap<String, float[]>();
        try {
            List<String> lines_list = Files.lines(CordPathEmbedding).collect(Collectors.toList());
            for (String line : lines_list) {
                List<String> items = Arrays.asList(line.split(","));
                String cord_uid = items.get(0);
                float[] embeddings = new float[items.size() - 1]; 
                for (int i = 1; i < items.size(); i++) {
                    try {
                        float embedding = Float.parseFloat(items.get(i));
                        embeddings[i-1] = embedding;
                    } catch (NumberFormatException e) {
                        System.err.println("Error reading embedding: " + items.get(i));
                        e.printStackTrace();
                    }
                }
                embeddingsMap.put(cord_uid, embeddings);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // leemos el csv y lo guardamos en una lista de objetos Metadata
        List<Metadata> metadataList;
        try {
            metadataList = readAllValues(CordPath, reader);
        } catch (IOException ex) {
            System.err.println("Error when trying to read and parse the input file");
            ex.printStackTrace();
            return;
        }

        if (args.length < 1) {
            System.out.println("Usage: java ParserIndexer indexFolder");
            return;
        }

        String indexFolder = args[0];

        IndexWriterConfig config = new IndexWriterConfig(new EnglishAnalyzer());
        IndexWriter writer = null;

        try {
            writer = new IndexWriter(FSDirectory.open(Paths.get(indexFolder)), config);
        } catch (CorruptIndexException e1) {
            System.out.println("Graceful message: exception " + e1);
            e1.printStackTrace();
        } catch (LockObtainFailedException e1) {
            System.out.println("Graceful message: exception " + e1);
            e1.printStackTrace();
        } catch (IOException e1) {
            System.out.println("Graceful message: exception " + e1);
            e1.printStackTrace();
        }

        for (Metadata metadata : metadataList) {

            Document doc = new Document();

            doc.add(new StringField("cordUid", metadata.cordUid(), Store.YES));
            doc.add(new TextField("title", metadata.title(), null));
            doc.add(new TextField("docAbstract", metadata.docAbstract(), null));
            doc.add(new StringField("journal", metadata.journal(), null));

            String jsonFilename = metadata.pdf_json_files();
            jsonFilename = jsonFilename.split("/")[jsonFilename.split("/").length - 1];
            HashMap<String, String> jsonDoc = readJSONpath(collectionPathJSON.resolve(jsonFilename));

            if (jsonDoc != null) {
                for (Map.Entry<String, String> entry : jsonDoc.entrySet()) {
                    if (!entry.getValue().equals("")) {
                        doc.add(new TextField(entry.getKey(), entry.getValue(), null));
                    }
                }
            }

            float[] embeddings = embeddingsMap.get(metadata.cordUid());
            doc.add(new KnnVectorField("embeddings", embeddings));

            try {

                writer.addDocument(doc);
            } catch (CorruptIndexException e) {
                System.out.println("Graceful message: exception " + e);
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println("Graceful message: exception " + e);
                e.printStackTrace();
            }
        }

        try {
            writer.commit();
            writer.close();
        } catch (CorruptIndexException e) {
            System.out.println("Graceful message: exception " + e);
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("Graceful message: exception " + e);
            e.printStackTrace();
        }
    }
}
