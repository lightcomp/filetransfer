package com.lightcomp.ft;

import javax.xml.ws.Endpoint;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.junit.Test;

import com.lightcomp.ft.receiver.ReceiverService;
import com.lightcomp.ft.sender.SenderConfig;
import com.lightcomp.ft.sender.SenderService;
import com.lightcomp.ft.sender.Transfer;

public class TransferTest {

    public static void publishEndpoint() {
        BeginTransferListenerImpl listenerImpl = new BeginTransferListenerImpl();
        ReceiverService receiver = FileTransfer.createReceiverService(listenerImpl);

        Bus bus = BusFactory.newInstance().createBus();
        BusFactory.setThreadDefaultBus(bus);
        
        Endpoint.publish("http://localhost:7979/ws", receiver.getImplementor());
        
        receiver.start();
    }
    
    @Test
    public void testConnection() throws InterruptedException {
        publishEndpoint();
        
        SenderConfig cfg = new SenderConfig("http://localhost:7979/ws");
        SenderService sender = FileTransfer.createSenderService(cfg);
        sender.start();
        
        TransferRequestImpl requestImpl = new TransferRequestImpl();
        Transfer transfer = sender.beginTransfer(requestImpl);
        
        Thread.sleep(20000000);
        
        
    }
}
