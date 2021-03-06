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

import com.j2bugzilla.base.BugzillaConnector;
import com.j2bugzilla.base.Product;
import com.j2bugzilla.rpc.GetProduct;

/**
 * OSLC CM Change Request Service
 */
public class ServiceProviderCatalogService extends HttpServlet {

    private static final long serialVersionUID = -5280734755943517104L;

    public ServiceProviderCatalogService() {
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            System.err.println("SPC.doGET Accept: " + request.getHeader("Accept"));
            final String dispatchTo;
            if (AcceptType.willAccept("text/html", request) && BugzillaInitializer.isProvideHtml()) {
                dispatchTo = "/cm/serviceprovidercatalog_html.jsp";

            } else if (AcceptType.willAccept("application/rdf+xml", request) ||
            		AcceptType.willAccept("application/xml", request)) {
                dispatchTo = "/cm/serviceprovidercatalog_rdfxml.jsp";

            } else {
                response.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE);
                return;
            }

            final BugzillaConnector bc = BugzillaInitializer.getBugzillaConnector(request);
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

            response.setHeader("OSLC-Core-Version", "2.0");
            final RequestDispatcher rd = request.getRequestDispatcher(dispatchTo);
            rd.forward(request, response);
            response.flushBuffer();

		} catch (UnauthroziedException e) {
			HttpUtils.sendUnauthorizedResponse(response, e);
        } catch (Throwable e) {
            throw new ServletException(e);
        }
    }
}
