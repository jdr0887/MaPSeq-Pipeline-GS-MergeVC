package edu.unc.mapseq.ws;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.soap.SOAPBinding;

import org.junit.Test;

import edu.unc.mapseq.ws.gs.mergevc.GSMergeVCService;

public class ServiceImplTest {

    @Test
    public void testAssertDirectoryExists() {
        QName serviceQName = new QName("http://vc.gs.ws.mapseq.unc.edu", "GSVariantCallingService");
        QName portQName = new QName("http://vc.gs.ws.mapseq.unc.edu", "GSVariantCallingPort");
        Service service = Service.create(serviceQName);
        service.addPort(portQName, SOAPBinding.SOAP11HTTP_MTOM_BINDING,
                String.format("http://%s:%d/cxf/GSVariantCallingService", "152.54.3.109", 8181));
        GSMergeVCService variantCallingService = service.getPort(GSMergeVCService.class);
        System.out.println(variantCallingService.getMetrics("FakeOSI02"));
    }

}
