package com.lightcomp.ft.exception;

class MessageParam implements Comparable<MessageParam> {

    public final String name;

    public final Object value;

    public final Integer position;

    public MessageParam(String name, Object value, Integer position) {
        this.name = name;
        this.value = value;
        this.position = position;
    }

    public void write(StringBuilder sb) {
        sb.append(", ").append(name).append('=').append(value);
    }

    @Override
    public int compareTo(MessageParam o) {
        if (position == null) {
            return o.position != null ? 1 : 0;
        }
        if (o.position == null) {
            return -1;
        }
        return position.compareTo(o.position);
    }
}