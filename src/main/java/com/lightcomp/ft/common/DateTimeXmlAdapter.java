package com.lightcomp.ft.common;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.apache.commons.lang3.StringUtils;

public class DateTimeXmlAdapter extends XmlAdapter<String, ZonedDateTime> {

    @Override
    public ZonedDateTime unmarshal(String dateTime) {
        if (StringUtils.isEmpty(dateTime)) {
            return null;
        }
        ZonedDateTime result = ZonedDateTime.parse(dateTime, DateTimeFormatter.ISO_DATE_TIME);
        return result;
    }

    @Override
    public String marshal(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ISO_DATE_TIME);
    }
}
