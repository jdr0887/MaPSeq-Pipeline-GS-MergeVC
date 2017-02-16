package edu.unc.mapseq.ws.gs.mergevc;

import java.util.List;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.ws.BindingType;
import javax.xml.ws.soap.MTOM;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_EMPTY)
@MTOM(enabled = true, threshold = 0)
@BindingType(value = javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_MTOM_BINDING)
@WebService(targetNamespace = "http://mergevc.gs.ws.mapseq.unc.edu", serviceName = "GSMergeVCService", portName = "GSMergeVCPort")
@SOAPBinding(style = Style.DOCUMENT, use = Use.LITERAL, parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
@Path("/GSMergeVCService/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface GSMergeVCService {

    @GET
    @Path("/getMetrics/{subjectName}")
    @WebMethod
    public List<MetricsResult> getMetrics(@PathParam("subjectName") @WebParam(name = "subjectName") String subjectName);

}
