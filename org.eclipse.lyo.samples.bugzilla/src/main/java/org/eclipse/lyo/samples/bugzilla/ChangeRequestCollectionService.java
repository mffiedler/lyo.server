/*******************************************************************************
 * Copyright (c) 2011, 2012 IBM Corporation.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *  
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *  
 *  Contributors:
 *  
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.lyo.samples.bugzilla;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.lyo.samples.bugzilla.exception.UnauthroziedException;
import org.eclipse.lyo.samples.bugzilla.resources.BugzillaChangeRequest;
import org.eclipse.lyo.samples.bugzilla.resources.Person;
import org.eclipse.lyo.samples.bugzilla.resources.QueryResponse;
import org.eclipse.lyo.samples.bugzilla.resources.ResponseInfo;
import org.eclipse.lyo.samples.bugzilla.utils.AcceptType;
import org.eclipse.lyo.samples.bugzilla.utils.HttpUtils;
import org.eclipse.lyo.samples.bugzilla.utils.RdfUtils;

import thewebsemantic.Bean2RDF;
import thewebsemantic.RDF2Bean;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.j2bugzilla.base.Bug;
import com.j2bugzilla.base.BugzillaConnector;
import com.j2bugzilla.base.BugzillaException;
import com.j2bugzilla.base.ConnectionException;
import com.j2bugzilla.base.Product;
import com.j2bugzilla.rpc.BugSearch;
import com.j2bugzilla.rpc.GetProduct;
import com.j2bugzilla.rpc.ReportBug;


/**
 * OSLC CM Change Request Service
 */
public class ChangeRequestCollectionService extends HttpServlet {    	
	private static final long serialVersionUID = -5280734755943517104L; 
	
    public ChangeRequestCollectionService() {}
    
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {    	
    	
    	String lang = null;
		if (request.getContentType().startsWith("application/rdf+xml")
				|| request.getContentType().startsWith("application/xml")) {
    		lang = RdfUtils.JENA_LANG_RDF_XML;
    	} else if (request.getContentType().startsWith("text/turtle")) {
    		lang = RdfUtils.JENA_LANG_TURTLE;
    	} else {    	
    		response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
    		return;
    	}

		Collection<BugzillaChangeRequest> changeRequests = readChangeRequests(
				request, lang);
		if (changeRequests.isEmpty()) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
    	
		try {	
			createChangeRequests(request, response, changeRequests);
            response.setHeader("OSLC-Core-Version", "2.0");
            response.setStatus(HttpServletResponse.SC_CREATED);
		} catch (UnauthroziedException e) {
			HttpUtils.sendUnauthorizedResponse(response, e);
		} catch (Exception e) {
			throw new ServletException(e);
		}
    }

	private void createChangeRequests(HttpServletRequest request,
			HttpServletResponse response,
			Collection<BugzillaChangeRequest> changeRequests)
			throws ConnectionException, BugzillaException,
			UnauthroziedException, IOException, ServletException {
		BugzillaConnector bc = BugzillaInitializer
				.getBugzillaConnector(request);

		for (BugzillaChangeRequest cr : changeRequests) {
			ReportBug reportBug = new ReportBug(cr.toBug());
			bc.executeMethod(reportBug);
			String bugUri = URLStrategy.getChangeRequestURL(reportBug
					.getID());
			response.addHeader("Location", bugUri);
		}
	}

	private Collection<BugzillaChangeRequest> readChangeRequests(
			HttpServletRequest request, String lang) throws IOException {
		String dummyBase = BugzillaInitializer.getBaseUri() + "/changerequest";
		Model model = ModelFactory.createDefaultModel();
		model.read(request.getInputStream(), dummyBase, lang);

		RDF2Bean reader = new RDF2Bean(model);
		reader.bind(BugzillaChangeRequest.class);
		reader.bind(Person.class);

		return reader.load(BugzillaChangeRequest.class);
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		int page = 0;
		int limit = 20;
		
		Product product = null;
		try {
			BugzillaConnector bc = BugzillaInitializer.getBugzillaConnector(request);
			
			String pageString = request.getParameter("page");
			if (null != pageString) {
				page = Integer.parseInt(pageString);
			}
			int productId = Integer.parseInt(request.getParameter("productId"));

			GetProduct getProducts = new GetProduct(productId); 
			bc.executeMethod(getProducts);
			product = getProducts.getProduct();
			
		} catch (UnauthroziedException e) {
			HttpUtils.sendUnauthorizedResponse(response, e);
			return;
		} catch (Exception e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		try {
			

			BugzillaConnector bc = BugzillaInitializer.getBugzillaConnector(request);
			
			BugSearch bugSearch = createBugSearch(page, limit, product);			
			bc.executeMethod(bugSearch);
			List<Bug> results = bugSearch.getSearchResults();
			
            response.setHeader("OSLC-Core-Version", "2.0");
			
			if (AcceptType.willAccept("text/html", request) && BugzillaInitializer.isProvideHtml()) {
				request.setAttribute("results", results);
				
	            request.setAttribute("product", product);
	            request.setAttribute("bugzillaUri", BugzillaInitializer.getBugzillaUri());
	            request.setAttribute("queryUri", 
	                    URLStrategy.getChangeRequestCollectionURL(product.getID()) 
	                    + "&oslc.paging=true");
	            
	            if (results.size() > limit) { 
	    			results.remove(results.size() - 1); // remove that one extra bug
	            	request.setAttribute("nextPageUri", 
	                    URLStrategy.getChangeRequestCollectionURL(product.getID()) 
	                    + "&amp;oslc.paging=true&amp;page=" + (page + 1));
	            }		

	            final RequestDispatcher rd = request.getRequestDispatcher("/cm/changerequest_collection_html.jsp");  
				rd.forward(request, response);
				response.flushBuffer();
			} else if (AcceptType.willAccept("application/rdf+xml", request)
					|| AcceptType.willAccept("application/xml", request)) {
				ResponseInfo responseInfo = new ResponseInfo();
				responseInfo.setTitle("Bugzilla Query Result");
				responseInfo.setNextPage(new URI(URLStrategy
						.getChangeRequestCollectionURL(product.getID())
						+ "&oslc.paging=true&page=" + (page + 1)));
				responseInfo.setUri(new URI(request.getRequestURL().append('?')
						.append(request.getQueryString()).toString()));

				QueryResponse queryResult = new QueryResponse();
				// This must match the query capability base.
				queryResult.setUri(new URI(URLStrategy.getChangeRequestCollectionURL(product.getID())));
				for (Bug bug : results) {
					BugzillaChangeRequest changeRequest = BugzillaChangeRequest.fromBug(bug);
					queryResult.getMembers().add(changeRequest);
				}
				
				Model m = RdfUtils.createModel();
				Bean2RDF writer = new Bean2RDF(m);
				writer.save(responseInfo);
				writer.save(queryResult);
		        response.setHeader("Content-Type", "application/rdf+xml");
				RdfUtils.writeModel(response, m, RdfUtils.JENA_LANG_ABBREVIATED_RDF_XML);
			} else {
				response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
			}
		} catch (UnauthroziedException e) {
			HttpUtils.sendUnauthorizedResponse(response, e);
		} catch (Throwable e) {
			throw new ServletException(e);
		}
	}

	protected BugSearch createBugSearch(int page, int limit, Product product) {
		BugSearch.SearchQuery productQuery = new BugSearch.SearchQuery(
				BugSearch.SearchLimiter.PRODUCT, product.getName());
		BugSearch.SearchQuery limitQuery = new BugSearch.SearchQuery(
				BugSearch.SearchLimiter.LIMIT, (limit + 1) + "");
		BugSearch.SearchQuery offsetQuery = new BugSearch.SearchQuery(
				BugSearch.SearchLimiter.OFFSET, (page * limit) + "");
		
		return new BugSearch(productQuery, limitQuery, offsetQuery);
	}
}
