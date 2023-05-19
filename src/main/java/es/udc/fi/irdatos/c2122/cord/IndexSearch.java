package es.udc.fi.irdatos.c2122.cord;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;
import java.io.FileWriter;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryRescorer;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.FileReader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class IndexSearch {

	/**
	 * Project testlucene9_0_0 SimpleSearch class reads the index SimpleIndex
	 * created with the SimpleIndexing class, creates and Index Searcher and search
	 * for documents which contain the word "probability" in the field
	 * "modelDescription" using the StandardAnalyzer Also contains and example
	 * sorting the results by reverse document number (index order). Also contains
	 * an example of a boolean programmatic query
	 * 
	 */
	// ---
	private static final Path DEFAULT_COLLECTION_PATH = Paths.get("src", "main", "resources");
	private static String xmlFilename = "topics.xml";
	private static String queryEmbeddingsFilename = "query_embeddings.txt";

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
            System.out.println("1. Número de documentos sobre los que se va a calcular el MAP (\"10\", \"100\" o \"1000\"), que tiene que coincidir con el número de documentos indicado en el programa 'IndexSearch.java', , excepto cuando se trate del método 3 que tendrá que recibir n/10");
            System.exit(0);
        }

		if (args.length == 0) {
			System.out.println("Usage: java SimpleSearch SimpleIndex");
			System.out.println(args.length);
			return;
		}

		Path collectionPath = DEFAULT_COLLECTION_PATH;

		Path XMLPath = collectionPath.resolve(xmlFilename);
		File xmlFile = new File(XMLPath.toString());
		Path queryEmbeddingsPath = collectionPath.resolve(queryEmbeddingsFilename);
		File queryEmbeddingsFile = new File(queryEmbeddingsPath.toString());

		IndexReader reader = null;
		Directory dir = null;
		IndexSearcher searcher = null;
		QueryParser parser = null;
		Query query = null;

		Map<String, float[]> QueryEmbeddingsMap = new HashMap<String, float[]>();
		try {
			// Leemos el fichero de embeddings y lo almacenamos en formato String
            BufferedReader buffer_reader = new BufferedReader(new FileReader(queryEmbeddingsFile));
			String content = "";
			String line;
			while ((line = buffer_reader.readLine()) != null) {
				content += line;
        }
        buffer_reader.close();
	
		// Convertir el contenido del String a formato json
		Gson gson = new Gson();
		JsonObject jsonObject = gson.fromJson(content, JsonObject.class);

		// Almacenar los quey_embeddings en un Map
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String nombreCampo = entry.getKey();
            JsonElement valorElement = entry.getValue();
            float[] valores = gson.fromJson(valorElement, float[].class);
            QueryEmbeddingsMap.put(nombreCampo, valores);
        }

        } catch (IOException e) {
            e.printStackTrace();
        }

		// Definimos el directorio donde se encuentra el índice, y el reader para leerlo
		try {
			dir = FSDirectory.open(Paths.get(args[0]));
			reader = DirectoryReader.open(dir);

		} catch (CorruptIndexException e1) {
			System.out.println("Graceful message: exception " + e1);
			e1.printStackTrace();
		} catch (IOException e1) {
			System.out.println("Graceful message: exception " + e1);
			e1.printStackTrace();
		}

		searcher = new IndexSearcher(reader);

		// Leemos el fichero XML con las queries
		XmlMapper xmlMapper = new XmlMapper();
		List<ParserXML> parsersXML = new ArrayList<ParserXML>();
		try {
			parsersXML = xmlMapper.readValue(xmlFile, new TypeReference<List<ParserXML>>() {
			});
		} catch (IOException e) {
			System.err.println("Error reading xml file: " + XMLPath);
			e.printStackTrace();
			return;
		}

		String search_method = "";

		try {
			search_method = args[1];

		} catch (Exception e) {
			System.err.println("Error reading search method: " + args[1]);
			e.printStackTrace();
			return;
		}

		Integer n;
		try {
			n = Integer.parseInt(args[2]);
		} catch (Exception e) {
			System.err.println("Error reading n: " + args[2]);
			e.printStackTrace();
			return;
		}

		Map<String, Float> boosts = new HashMap<String, Float>();

		parsersXML.remove(0);
		// Recorremos las queries y definimos los métodos de búsqueda
		for (ParserXML topic : parsersXML) {

			String query_number = topic.number();
			if (!search_method.equals("2")) {
				parser = new MultiFieldQueryParser(new String[] { "title", "docAbstract", "Introduction", "Others" },
						new EnglishAnalyzer());
			} else {
				boosts.put("title", 2.0f);
				boosts.put("docAbstract", 1.2f);
				boosts.put("Introduction", 0.5f);
				boosts.put("Others", 0.5f);
				parser = new MultiFieldQueryParser(new String[] { "title", "docAbstract", "Introduction", "Others" },
						new EnglishAnalyzer(), boosts);
			}

			try {
				query = parser.parse(topic.query());
			} catch (ParseException e) {
				e.printStackTrace();
			}

			KnnVectorQuery knnvectorquery = null;
			if (search_method.equals("3")){
				float [] topic_emb = QueryEmbeddingsMap.get(topic.number());
				knnvectorquery = new KnnVectorQuery("embedding", topic_emb, n);
			}
			TopDocs topDocs = null;
			TopDocs topDocs_embeddings = null;

			try {
				topDocs = searcher.search(query, n);
			} catch (IOException e1) {
				System.out.println("Graceful message: exception " + e1);
				e1.printStackTrace();
			}

			try {
				topDocs_embeddings = QueryRescorer.rescore(searcher, topDocs, knnvectorquery, 0.9, n/10);
			} catch (IOException e1){
				e1.printStackTrace();
			}

			String nombreArchivo = "results/results" + query_number + ".txt";
			try {
				File carpeta = new File("results");
				carpeta.mkdir();
				FileWriter writer = new FileWriter(nombreArchivo);
				if (!search_method.equals("3")){
					String currentDocID = "";
					for (int i = 0; i < Math.min(n, topDocs.totalHits.value); i++) {
						if (reader.document(topDocs.scoreDocs[i].doc).get("cordUid").equals(currentDocID)) {
							writer.write(query_number + " Q0 " + reader.document(topDocs.scoreDocs[i].doc).get("cordUid")
									+ " " + i + " " + topDocs.scoreDocs[i].score + " borja-nina-uxio\n");
						}
						writer.write(query_number + " Q0 " + reader.document(topDocs.scoreDocs[i].doc).get("cordUid")
								+ " " + i + " " + topDocs.scoreDocs[i].score + " borja-nina-uxio\n");
						currentDocID = reader.document(topDocs.scoreDocs[i].doc).get("cordUid");
					}
				} else {
					String currentDocID = "";
					for (int i = 0; i < Math.min(n/10, topDocs_embeddings.totalHits.value); i++) {
						if (reader.document(topDocs_embeddings.scoreDocs[i].doc).get("cordUid").equals(currentDocID)) {
							writer.write(query_number + " Q0 " + reader.document(topDocs_embeddings.scoreDocs[i].doc).get("cordUid")
									+ " " + i + " " + topDocs_embeddings.scoreDocs[i].score + " borja-nina-uxio\n");
						}
						writer.write(query_number + " Q0 " + reader.document(topDocs_embeddings.scoreDocs[i].doc).get("cordUid")
								+ " " + i + " " + topDocs_embeddings.scoreDocs[i].score + " borja-nina-uxio\n");
						currentDocID = reader.document(topDocs_embeddings.scoreDocs[i].doc).get("cordUid");
					}
				}
			
				
				writer.close();
			} catch (CorruptIndexException e) {
				System.out.println("Graceful message: exception " + e);
				e.printStackTrace();
			} catch (IOException e) {
				System.out.println("Ha ocurrido un error al escribir en el archivo " + nombreArchivo);
				e.printStackTrace();
			}

		}

		try {
			reader.close();
			dir.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
