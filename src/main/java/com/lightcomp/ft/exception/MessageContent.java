package com.lightcomp.ft.exception;

class MessageContent implements MessagePart {

    private final String content;

    public MessageContent(String content) {
        this.content = content;
    }

    @Override
    public void write(StringBuilder sb) {
        sb.append(content);
    }

    @Override
    public Integer getPosition() {
        return Integer.MIN_VALUE;
    }

    @Override
    public void writeSeparator(StringBuilder sb, MessagePart nextMessage) {
        sb.append(", ");
    }
}
