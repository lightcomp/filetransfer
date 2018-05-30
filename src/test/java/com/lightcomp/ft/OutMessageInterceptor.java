package com.lightcomp.ft;

import org.apache.commons.lang3.Validate;
import org.apache.cxf.jaxws.interceptors.HolderOutInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public abstract class OutMessageInterceptor extends AbstractPhaseInterceptor<Message> {

    public OutMessageInterceptor() {
        super(Phase.PRE_LOGICAL);
        addBefore(HolderOutInterceptor.class.getName());
    }

    @Override
    public void handleMessage(Message message) {
        MessageContentsList outObjects = MessageContentsList.getContentsList(message);
        Validate.isTrue(outObjects.size() == 1);
        Object obj = outObjects.get(0);
        handleMessage(obj);
    }

    protected abstract void handleMessage(Object obj);
}