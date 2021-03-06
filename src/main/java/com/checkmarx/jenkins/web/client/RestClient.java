package com.checkmarx.jenkins.web.client;

import java.io.Closeable;
import java.util.Map;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;

import com.checkmarx.jenkins.web.model.AnalyzeRequest;
import com.checkmarx.jenkins.web.model.AuthenticationRequest;
import com.checkmarx.jenkins.web.model.CxException;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryRequest;
import com.checkmarx.jenkins.web.model.GetOpenSourceSummaryResponse;
import net.sf.json.JSONSerializer;
import org.jetbrains.annotations.NotNull;


/**
 * @author tsahi
 * @since 02/02/16
 */
public class RestClient implements Closeable {
    private static final String ROOT_PATH = "CxRestAPI/";
    private static final String AUTHENTICATION_PATH = "auth/login";
    private static final String ANALYZE_SUMMARY_PATH = "projects/{projectId}/opensourceanalysis/summaryresults";
    private static final String ANALYZE_PATH = "projects/{projectId}/opensourcesummary";
    private static final String FAILED_TO_CONNECT_CX_SERVER_ERROR = "connection to checkmarx server failed";
    private static final String CX_COOKIE = "cxCookie";
    private static final String CSRF_COOKIE = "CXCSRFToken";

    private AuthenticationRequest authenticationRequest;
    private Client client;
    private WebTarget root;

    public RestClient(String serverUri, AuthenticationRequest authenticationRequest) {
        this.authenticationRequest = authenticationRequest;
        client = ClientBuilder.newClient();
        root = client.target(serverUri).path(ROOT_PATH);
    }

    public void analyzeOpenSources(AnalyzeRequest request) {
        Entity<Form> entity = createAnalyzeFormData(request);
        Map<String, NewCookie> cookies = authenticate();
        Invocation invocation = root.path(ANALYZE_PATH)
                .resolveTemplate("projectId", request.getProjectId())
                .request()
                .cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .buildPost(entity);
        Response response = invokeRequet(invocation);
        validateResponse(response);
    }

    @NotNull
    private Entity<Form> createAnalyzeFormData(AnalyzeRequest request) {
        Form form = new Form()
                .param("ProjectId", Long.toString(request.getProjectId()))
                .param("Origin", Integer.toString(AnalyzeRequest.JENKINS_ORIGIN))
                .param("HashValues", JSONSerializer.toJSON(request.getHashValues()).toString());
        return Entity.form(form);
    }

    public GetOpenSourceSummaryResponse getOpenSourceSummary(GetOpenSourceSummaryRequest request) {
        Map<String, NewCookie> cookies = authenticate();
        Invocation invocation = root.path(ANALYZE_SUMMARY_PATH)
                .resolveTemplate("projectId", request.getProjectId())
                .request()
                .cookie(cookies.get(CX_COOKIE))
                .cookie(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .header(CSRF_COOKIE, cookies.get(CSRF_COOKIE).getValue())
                .buildGet();
        Response response = invokeRequet(invocation);
        validateResponse(response);
        return response.readEntity(GetOpenSourceSummaryResponse.class);
    }

    private Map<String, NewCookie> authenticate() {
        Invocation invocation = root.path(AUTHENTICATION_PATH)
                .request()
                .buildPost(Entity.entity(authenticationRequest, MediaType.APPLICATION_JSON));
        Response response = invokeRequet(invocation);
        validateResponse(response);

        return response.getCookies();
    }

    private Response invokeRequet(Invocation invocation) {
        try {
            return invocation.invoke();
        } catch (ProcessingException exc) {
            return ThrowFailedToConnectCxServerError();
        }
    }

    private void validateResponse(Response response) {
        int httpStatus = response.getStatus();
        if (httpStatus < 400) return;
        if (httpStatus == Response.Status.SERVICE_UNAVAILABLE.getStatusCode())
            ThrowFailedToConnectCxServerError();
        else
            ThrowCxException(response);
    }

    private Response ThrowFailedToConnectCxServerError() {
        throw new WebApplicationException(FAILED_TO_CONNECT_CX_SERVER_ERROR);
    }

    private void ThrowCxException(Response response) {
        CxException cxException = response.readEntity(CxException.class);
        throw new WebApplicationException(cxException.getMessageCode() + "\n" + cxException.getMessageDetails(), response);
    }

    @Override
    public void close() {
        client.close();
    }
}
