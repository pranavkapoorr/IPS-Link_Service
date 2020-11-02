package com.ips.ipslink.resources;

import java.io.*;
import java.util.*;
import org.apache.logging.log4j.Logger;

public class LanguageLoader {
    public static HashMap<String, ArrayList<String>> loadLanguages(Logger log) {
        HashMap<String, ArrayList<String>> languages = new HashMap<>();
        Properties config = new Properties();  
            InputStream in;
            try {
                in = new FileInputStream("configs/language.properties");
                config.load(in);
                log.trace("loading languages..");
                in.close();
            } catch (IOException e2) {
                log.trace(e2.getMessage());
            }
        languages.put("Payment",new ArrayList<String>(Arrays.asList(config.getProperty("Payment","PURCHASE").split(";"))));
        languages.put("Refund",new ArrayList<String>(Arrays.asList(config.getProperty("Refund","REFUND").split(";"))));
        languages.put("Reversal",new ArrayList<String>(Arrays.asList(config.getProperty("Reversal","REVERSAL").split(";"))));
        languages.put("Card_Removed",new ArrayList<String>(Arrays.asList(config.getProperty("Card_Removed","CARD REMOVED").split(";"))));
        languages.forEach((k,v)->v.forEach(e->log.trace(k +" "+e)));
        log.trace("loaded languages in to map");
        return languages;
    }
}
