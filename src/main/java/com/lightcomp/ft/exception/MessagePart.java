package com.lightcomp.ft.exception;

interface MessagePart extends Comparable<MessagePart> {

    Integer getPosition();

    void write(StringBuilder sb);

    void writeSeparator(StringBuilder sb, MessagePart nextMessage);
    
    @Override
    default int compareTo(MessagePart o) {
        Integer tp = getPosition();
        Integer op = o.getPosition();

        if (tp == null) {
            return op != null ? 1 : 0;
        }
        if (op == null) {
            return -1;
        }
        return tp.compareTo(op);
    }
}