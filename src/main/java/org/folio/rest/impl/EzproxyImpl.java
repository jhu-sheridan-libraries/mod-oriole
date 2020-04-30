package org.folio.rest.impl;

import com.google.common.net.InternetDomainName;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.folio.rest.jaxrs.model.*;
import org.folio.rest.jaxrs.resource.Ezproxy;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.ValidationHelper;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class EzproxyImpl implements Ezproxy {
  private static final Logger LOGGER = LoggerFactory.getLogger(EzproxyImpl.class);
  public static final String RESOURCE_TABLE = "resource";
  private static final String ID_FIELD_NAME = "id";
  private static final String RESOURCE_SCHEMA_PATH = "ramls/schemas/resource.json";
  private static final String SUBJECT_SCHEMA_PATH = "ramls/schemas/subject.json";
  private static final List<String> AVOID_DOMAINS = Arrays.asList("jhu.edu","library.jhu.edu","mse.jhu.edu","ac.uk","co.uk");
  private static final List<String> OMIT_DATABASES = Arrays.asList("JHU05048","JHU04485","JHU02980","JHU03588","JHU04456","JHU03659","JHU04935");
  private String RESOURCE_SCHEMA = null;
  private String SUBJECT_SCHEMA = null;

  public EzproxyImpl(Vertx vertx, String tennantId) {
    if (RESOURCE_SCHEMA == null || SUBJECT_SCHEMA == null) {
      initCQLValidation();
    }
    PostgresClient.getInstance(vertx, tennantId).setIdField(ID_FIELD_NAME);
  }

  private void initCQLValidation() {
    try {
      InputStream ris = getClass().getClassLoader().getResourceAsStream(RESOURCE_SCHEMA_PATH);
      assert ris != null;
      RESOURCE_SCHEMA = IOUtils.toString(ris, StandardCharsets.UTF_8);
    } catch (Exception e) {
      LOGGER.error("Unable to load schema - " + RESOURCE_SCHEMA_PATH
              + ", validation of query fields will not be active");
    }
    try {
      InputStream sis = getClass().getClassLoader().getResourceAsStream(SUBJECT_SCHEMA_PATH);
      assert sis != null;
      SUBJECT_SCHEMA = IOUtils.toString(sis, StandardCharsets.UTF_8);
    } catch (Exception e) {
      LOGGER.error("Unable to load schema - " + SUBJECT_SCHEMA_PATH
              + ", validation of query fields will not be active");
    }
  }

  public void getEzproxy(
          Map<String, String> okapiHeaders,
          Handler<AsyncResult<Response>> asyncResultHandler,
          Context vertxContext) {
    vertxContext.runOnContext(v -> {  // TODO: Is this necessary?
      PostgresClient client = ApiUtil.getPostgresClient(okapiHeaders, vertxContext);
      String sql = "SELECT jsonb -> 'url' as url, jsonb ->'altId' as altId, jsonb ->'title' as title, " +
              "jsonb -> 'availability' as availability FROM " + RESOURCE_TABLE + " where jsonb ->> 'proxy' = 'true'";
      client.select(sql, (reply) -> {
        if (reply.succeeded()) {
          List<JsonArray> results = reply.result().getResults();
          List<Stanzas> stanzas = new ArrayList<>();

          //loop through results build a list of unique domains
          //also extract the altid, title and availabilities for the first unique value
          for (JsonArray result : results) {
            //TODO get by field name not position
            String url = cleanString(result.getString(0));
            String altId = cleanString(result.getString(1));
            String title = cleanString(result.getString(2));
            String availability = result.getString(3);
            JSONArray availabilityJson = new JSONArray(availability);
            List<String> availabilities = new ArrayList<>();
            Stanzas stanza = new Stanzas();

            //get and set url protocol and domain (removing first subdomain)
            try {
              stanza = getDomain(url,stanza);
              if (AVOID_DOMAINS.contains(stanza.getDomain())){
                continue;
              }
            } catch (MalformedURLException e) {
              e.printStackTrace();
            }

            //convert availablities/restrictions json to list
            for (Object item : availabilityJson) {
              availabilities.add((String) item);
            }

            //add stanza object to the list if the base domain is not already added
            boolean domainPresent = false;
            for (Stanzas s : stanzas) {
              if (s.getDomain().contains(stanza.getDomain())) {
                domainPresent = true;
                break;
              }
            }
            if (!domainPresent) {
              stanzas.add(createStanza(stanza, altId, title, availabilities));
            }
          }

          //loop through unique stanza subdomains add url and alitid for any item containing subdomain
          stanzas = setDatabaseUrlsAndIds(stanzas, results);

          //write the response text
          String response = null;
          try {
            response = writeEzproxyFile(stanzas);
            System.out.println(response);
          } catch (IOException e) {
            e.printStackTrace();
          }

          asyncResultHandler.handle(
                  Future.succeededFuture(GetEzproxyResponse.respond200WithTextPlain(response)));
        } else {
          ValidationHelper.handleError(reply.cause(), asyncResultHandler);
        }
      });
    });
  }

  public Stanzas createStanza(Stanzas stanza, String altId, String title, List<String> availabilities) {
    stanza.setAltid(altId);
    stanza.setTitle(title);
    stanza.setAvailability(availabilities);
    if (OMIT_DATABASES.contains(altId)){
      stanza.setOmitDb(true);
    }
    else {
      stanza.setOmitDb(false);
    }
    return stanza;
  }

  public List<Stanzas> setDatabaseUrlsAndIds(List<Stanzas> stanzas,List<JsonArray> results) {
    //loop through unique domains
    for (Stanzas stanza : stanzas) {
      //loop through all url looking for matches to the unique domain
      for (JsonArray result : results) {
        String url = cleanString(result.getString(0));
        String subdomain = null;
        try {
          subdomain = getSubdomain(url);
        } catch (MalformedURLException e) {
          e.printStackTrace();
        }
        String altId = result.getString(1);
        assert subdomain != null;
        if (subdomain.contains(stanza.getDomain())) {
          //add url to stanza object
          List<String> urls = stanza.getUrls();
          if (!urls.contains(subdomain)) {
            urls.add(subdomain);
            stanza.setUrls(urls);
          }
          //add alt id to stanza object
          List<String> altIds = stanza.getAltids();
          altIds.add(altId);
          stanza.setAltids(altIds);
        }
      }
    }
    return stanzas;
  }

  public String cleanString(String s) {
    s = s.replace("[", "")
            .replace("]", "")
            .replace(",", "")
            .replace("\"", "");
    return s;
  }

  public Stanzas getDomain(String url, Stanzas stanza) throws MalformedURLException {
    String domain;
    String protocol;
    URL aURL = new URL(url);
    try {
      //System.out.println("host = " + aURL.getHost());
      protocol = aURL.getProtocol();
      domain =  aURL.getHost();
      System.out.println("Host: " + aURL.getHost());
      System.out.println("Original: " + domain);
      int count = StringUtils.countMatches(domain, ".");
      //if count of . is > than 1
      if (count>1) {
        //domain = domain.substring(domain.substring(0, domain.lastIndexOf(".")).lastIndexOf(".")+1);
        //domain = domain.substring(domain.lastIndexOf("."));
        try {
          domain =InternetDomainName.from(new URL(url).getHost()).topPrivateDomain().toString();
          System.out.println("Top domain: " + domain);
        } catch (MalformedURLException e) {
          System.out.println("URL malformed - possible IP address");
        } catch (IllegalArgumentException e) {
          System.out.println("Unable to parse - possible IP address");
        }

      }
      stanza.setDomain(domain);
      stanza.setBaseURL(url);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return stanza;
  }

  public String getSubdomain(String url) throws MalformedURLException {
    URL aURL = new URL(url);
    return aURL.getHost();
  }

  public String writeEzproxyFile(List<Stanzas> stanzas) throws IOException {
    List<Stanzas> campusAffiliationStanzas = new ArrayList<>();
    List<Stanzas> omitStanzas = new ArrayList<>();
    StringBuilder sb = new StringBuilder();

    for (Stanzas stanza : stanzas) {
      if (stanza.getAvailability().size() == 0) {
        if (stanza.getOmitDb()) {
          omitStanzas.add(stanza);
        }
        sb.append("Title ")
                .append(stanza.getTitle())
                .append(" (")
                .append(stanza.getAltid())
                .append(")")
                .append("\n")
                .append("# Complete list of IDs for included databases: ")
                .append(stanza.getAltids().toString()
                        .replace("[", "")
                        .replace("]", "")
                        .replace(",", "")
                        .replace("\"", ""))
                .append("\n")
                .append("URL ")
                .append(stanza.getBaseURL())
                .append("\n")
                .append("DJ ")
                .append(stanza.getDomain())
                .append("\n");
        for (String url : stanza.getUrls()) {
          sb.append("HJ ").append(url).append("\n");
        }
        sb.append("\n").append("\n");
      }
      else if (stanza.getAvailability().size() == 1) {
        campusAffiliationStanzas.add(stanza);
      }
    }
    for (Stanzas stanza : campusAffiliationStanzas) {
      sb.append("# EZProxy group for campus affiliations: ")
              .append(stanza.getAvailability().toString()
                      .replace("[", "")
                      .replace("]", "")
                      .replace(",", "")
                      .replace("\"", ""))
              .append("\n")
              .append("Group ")
              .append(stanza.getAvailability().toString()
                      .replace("[", "")
                      .replace("]", "")
                      .replace(",", "")
                      .replace("\"", ""))
              .append("\n")
              .append("\n")
              .append("Title ")
              .append(stanza.getTitle())
              .append("(")
              .append(stanza.getAltid())
              .append(")")
              .append("\n")
              .append("URL ")
              .append(stanza.getBaseURL())
              .append("\n")
              .append("DJ ")
              .append(stanza.getDomain())
              .append("\n");
      for (String url : stanza.getUrls()) {
        sb.append("HJ ").append(url).append("\n");
      }
      sb.append("\n").append("\n");
    }
    for (Stanzas stanza : omitStanzas) {
      sb.append("# Omitted as per Xerxes ezp_exp_resourceid_omit config: ")
              .append(stanza.getAltid().replace("\"", ""))
              .append("\n")
              .append("# ")
              .append("\n");
    }
    return sb.toString();
  }
}