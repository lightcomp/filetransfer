package com.lightcomp.ft.exception;

class MessageParam implements MessagePart {

    public final String name;

    public final Object value;

    public final Integer position;

    public MessageParam(String name, Object value, Integer position) {
        this.name = name;
        this.value = value;
        this.position = position;
    }

    @Override
    public Integer getPosition() {
        return position;
    }

    @Override
    public void write(StringBuilder sb) {
        sb.append(name).append('=').append(value);
    }

    @Override
    public void writeSeparator(StringBuilder sb, MessagePart nextMessage) {
        sb.append(", ");
    }
}