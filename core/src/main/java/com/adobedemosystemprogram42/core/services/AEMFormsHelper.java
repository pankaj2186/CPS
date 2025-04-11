package com.adobedemosystemprogram42.core.services;

public interface AEMFormsHelper
	{
	 // public abstract com.adobe.aemfd.docmanager.Document flattenDocument(InputStream paramInputStream1, InputStream paramInputStream2);
	  
	  //public abstract com.adobe.aemfd.docmanager.Document assembleDocuments(List<String> paramList, com.adobe.aemfd.docmanager.Document paramDocument);
	  
	  public abstract com.adobe.aemfd.docmanager.Document orgW3CDocumentToAEMFDDocument(org.w3c.dom.Document paramDocument);
	  
	  public abstract org.w3c.dom.Document getOrgW3cDocument(String paramString);
	  
	  //public abstract com.adobe.aemfd.docmanager.Document simpleassemblyOfDocuments(List<String> paramList);
	  
	  public abstract com.adobe.aemfd.docmanager.Document getAEMFDDocument(String paramString);
	  
	  //public abstract List<com.adobe.aemfd.docmanager.Document> exportPDF(com.adobe.aemfd.docmanager.Document paramDocument);
	}
