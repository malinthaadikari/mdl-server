package org.mrdlib.recommendation;

import java.util.Random;

import org.mrdlib.display.DisplayDocument;
import org.mrdlib.display.DocumentSet;

public class RandomDocumentRecommenderLanguageRestricted extends RelatedDocumentsFromSolr{

	
	public RandomDocumentRecommenderLanguageRestricted() throws Exception {
		super();
		// TODO Auto-generated constructor stub
	}

	@Override
	public DocumentSet getRelatedDocumentSet(DisplayDocument requestDoc) throws Exception {
		// TODO Auto-generated method stub
		return getRelatedDocumentSet(requestDoc, 10);
	}

	//Simplest case that all documents of same collection are consecutive in the database
	@Override
	public DocumentSet getRelatedDocumentSet(DisplayDocument requestDoc, int numberOfRelatedDocs) throws Exception {
		Random random = new Random();
		long randomSeed = random.nextLong();
		random.setSeed(randomSeed);
		String seed = Integer.toString(random.nextInt());
		
		return scon.getRandomDocumentSet(requestDoc, numberOfRelatedDocs, true, seed);
	}

}
