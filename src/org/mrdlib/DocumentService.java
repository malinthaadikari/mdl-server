package org.mrdlib;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.mrdlib.database.DBConnection;
import org.mrdlib.database.NoEntryException;
import org.mrdlib.display.DisplayDocument;
import org.mrdlib.display.DocumentSet;
import org.mrdlib.display.RootElement;
import org.mrdlib.display.StatusMessage;
import org.mrdlib.display.StatusReport;
import org.mrdlib.display.StatusReportSet;
import org.mrdlib.ranking.ApplyRanking;
import org.mrdlib.recommendation.RecommenderFactory;
import org.mrdlib.recommendation.RelatedDocuments;
import org.mrdlib.solrHandler.NoRelatedDocumentsException;

/**
 * @author Millah
 * 
 *         This class is called by Tomcat and the start of the webapp
 */
@Path("documents/{documentId : [a-zA-Z0-9-_.,]+}")
// set Path and allow numbers, letters and -_., Save Path as document_id
public class DocumentService {

	// set up the necessary connections and load the config
	// DocumentExamples documentExample = new DocumentExamples();

	private Long requestRecieved;
	private DBConnection con = null;
	private Constants constants = null;
	private RootElement rootElement = null;
	private StatusReportSet statusReportSet = null;
	private ApplyRanking ar = null;
	private RelatedDocuments rdg = null;

	public DocumentService() {
		requestRecieved = System.currentTimeMillis();
		constants = new Constants();
		rootElement = new RootElement();
		statusReportSet = new StatusReportSet();
		try {
			con = new DBConnection("tomcat");
			ar = new ApplyRanking(con);
		} catch (Exception e) {
			statusReportSet.addStatusReport(new UnknownException(e, constants.getDebugModeOn()).getStatusReport());
		}
	}

	@GET
	// set end of Path
	@Path("related_documents")
	@Produces(MediaType.APPLICATION_XML + ";charset=utf-8")
	/**
	 * Get the related documentSet of a given document
	 * 
	 * @param documentIdOriginal
	 *            - id from the cooperation partner
	 * @return a document set of related documents
	 */
	public RootElement getRelatedDocumentSet(@PathParam("documentId") String documentIdOriginal) {
		DisplayDocument requestDocument = null;
		DocumentSet documentset = null;

		try {
			// get the requested document from the database
			requestDocument = con.getDocumentBy(constants.getIdOriginal(), documentIdOriginal);
			// get all related documents from solr
			Boolean validAlgorithmFlag = false;
			int numberOfAttempts = 0;
			while (!validAlgorithmFlag && numberOfAttempts < constants.getNumberOfRetries()) {
				try {
					rdg = RecommenderFactory.getRandomRDG(con, requestDocument);
					System.out.println(rdg.loggingInfo.get("name"));
					documentset = rdg.getRelatedDocumentSet(requestDocument, ar.getSolrRows());
					validAlgorithmFlag = true;
					// If no related documents are present, redo the algorithm
				} catch (NoRelatedDocumentsException e) {
					for (String key : rdg.loggingInfo.keySet()) {
						System.out.println(key + ":" + rdg.loggingInfo.get(key));
					}
					validAlgorithmFlag = false;
					numberOfAttempts++;
				}
			}

			if (validAlgorithmFlag) {
				if (numberOfAttempts > 0)
					System.out.printf("We retried %d times for document " + requestDocument.getDocumentId() + "\n",
							numberOfAttempts);
			} else {
				System.out.println("Using fallback recommender");
				rdg = RecommenderFactory.getFallback(con);
				documentset = rdg.getRelatedDocumentSet(requestDocument, ar.getSolrRows());
			}
			documentset.setRDG(rdg);
			documentset = ar.selectRandomRanking(documentset);
			// if there is no such document in the database
		} catch (NoEntryException e) {
			statusReportSet.addStatusReport(e.getStatusReport());
			// if retry limit has been reached and no related documents still
			// have been extracted
		} catch (NoRelatedDocumentsException e) {
			statusReportSet.addStatusReport(e.getStatusReport());
			// if something else happened there
		} catch (Exception e) {
			statusReportSet.addStatusReport(new UnknownException(e, constants.getDebugModeOn()).getStatusReport());
		}
		// if everything went ok
		if (statusReportSet.getSize() == 0)
			statusReportSet.addStatusReport(new StatusReport(200, new StatusMessage("ok", "en")));

		// add both the status message and the related document to the xml
		rootElement.setDocumentSet(documentset);
		rootElement.setStatusReportSet(statusReportSet);

		try {
			
			documentset = con.logRecommendationDelivery(requestDocument.getDocumentId(), requestRecieved, rootElement);

			for (DisplayDocument doc : documentset.getDocumentList()) {
				String url = "https://" + constants.getEnvironment() + ".mr-dlib.org/v1/recommendations/"
						+ doc.getRecommendationId() + "/original_url?access_key=" + doc.getAccessKeyHash()
						+ "&format=direct_url_forward";
				doc.setClickUrl(url);
			}
		} catch (Exception e) {
			statusReportSet.addStatusReport(new UnknownException(e, constants.getDebugModeOn()).getStatusReport());
		}

		try {
			if (con != null)
				con.close();
		} catch (Exception e) {
			statusReportSet.addStatusReport(new UnknownException(e, constants.getDebugModeOn()).getStatusReport());
		}
		return rootElement;
	}

	@GET
	@Produces("text/plain")
	public String getOriginalDoc() {
		return "Hello World ";
	}

}
