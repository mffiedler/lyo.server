/*******************************************************************************
 * Copyright (c) 2011 IBM Corporation.
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
import java.util.ArrayList;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.lyo.samples.bugzilla.exception.UnauthroziedException;
import org.eclipse.lyo.samples.bugzilla.jbugzx.rpc.GetAccessibleProducts;
import org.eclipse.lyo.samples.bugzilla.utils.AcceptType;
import org.eclipse.lyo.samples.bugzilla.utils.HttpUtils;
import org.eclipse.lyo.samples.bugzilla.utils.StringUtils;

import com.j2bugzilla.base.BugzillaConnector;
import com.j2bugzilla.base.Product;
import com.j2bugzilla.rpc.GetProduct;


/**
 * OSLC CM Change Request Service
 */
public class ServiceProviderService extends HttpServlet {    	
	private static final long serialVersionUID = -5280734755943517104L; 

    public ServiceProviderService() {}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.err.println("SP.doGET - Accept: " + request.getHeader("Accept"));

		try {
			int productId = Integer.parseInt(request.getParameter("productId"));
			
			BugzillaConnector bc = BugzillaInitializer.getBugzillaConnector(request);				
			GetProduct getProduct = new GetProduct(productId); 
			bc.executeMethod(getProduct);
			request.setAttribute("product", getProduct.getProduct());
			
		} catch (UnauthroziedException e) {
			HttpUtils.sendUnauthorizedResponse(response, e);
			return;
		} catch (Exception e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		try {
			final String dispatchTo;
			String accept = request.getHeader("Accept");
			
			if (StringUtils.isEmpty(accept) || AcceptType.willAccept("application/rdf+xml", request)
					|| AcceptType.willAccept("application/xml", request)) {	
				dispatchTo = "/cm/serviceprovider_rdfxml.jsp";

			} else	if (AcceptType.willAccept("text/html", request)) {
				dispatchTo = "/cm/serviceprovider_html.jsp";
			} else {
				response.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE);
				return;
			}	
			
			final BugzillaConnector bc = BugzillaInitializer.getBugzillaConnector(request);			
            request.setAttribute("baseUri", BugzillaInitializer.getBaseUri());
            request.setAttribute("bugzillaUri", BugzillaInitializer.getBugzillaUri());

			GetAccessibleProducts getProductIds = new GetAccessibleProducts();
			bc.executeMethod(getProductIds);
			Integer[] productIds = getProductIds.getIds();
			List<Product> products = new ArrayList<Product>();
			for (Integer product : productIds) {
				GetProduct getProductMethod = new GetProduct(product);
				bc.executeMethod(getProductMethod);
				products.add(getProductMethod.getProduct());
			}

			request.setAttribute("products", products);
            
			final RequestDispatcher rd = request.getRequestDispatcher(dispatchTo); 
			rd.forward(request, response);
			response.flushBuffer();

		} catch (UnauthroziedException e) {
			HttpUtils.sendUnauthorizedResponse(response, e);
		} catch (Throwable e) {
			throw new ServletException(e);
		}
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		// TODO Auto-generated method stub
		System.err.println("ServiceProvider POST");
	}
	
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		// TODO Auto-generated method stub
		System.err.println("ServiceProvider PUTT");
	}	
}
