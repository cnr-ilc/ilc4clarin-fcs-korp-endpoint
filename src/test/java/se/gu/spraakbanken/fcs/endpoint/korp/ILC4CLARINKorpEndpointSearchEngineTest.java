package se.gu.spraakbanken.fcs.endpoint.korp;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;

import eu.clarin.sru.server.CQLQueryParser;
import eu.clarin.sru.server.SRUConfigException;
import eu.clarin.sru.server.SRUDiagnosticList;
import eu.clarin.sru.server.SRUException;
import eu.clarin.sru.server.SRUQueryParserRegistry;
import eu.clarin.sru.server.SRUServerConfig;
import eu.clarin.sru.server.SRUVersion;
import eu.clarin.sru.server.utils.SRUServerServlet;
import eu.clarin.sru.server.fcs.EndpointDescription;
import eu.clarin.sru.server.fcs.ResourceInfo;
import eu.clarin.sru.server.fcs.FCSQueryParser;
import eu.clarin.sru.server.fcs.utils.SimpleEndpointDescriptionParser;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.testing.ServletTester;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import se.gu.spraakbanken.fcs.endpoint.korp.cqp.FCSToCQPConverter;
import se.gu.spraakbanken.fcs.endpoint.korp.data.json.pojo.info.*;
import se.gu.spraakbanken.fcs.endpoint.korp.data.json.pojo.query.Query;
import se.gu.spraakbanken.fcs.endpoint.korp.utils.ReadExternalPropFiles;

public class ILC4CLARINKorpEndpointSearchEngineTest {

    //private String jsonString = "{\"corpora\":{\"PAROLE\":{\"attrs\":{\"a\":[],\"p\":[\"word\",\"pos\",\"msd\",\"lemma\",\"lex\",\"saldo\",\"prefix\",\"suffix\",\"ref\",\"dephead\",\"deprel\"],\"s\":[\"sentence\",\"sentence_id\",\"text\",\"text_id\",\"text_date\",\"text_title\",\"text_publisher\",\"text_datefrom\",\"text_dateto\",\"text_timefrom\",\"text_timeto\"]},\"info\":{\"Charset\":\"utf8\",\"FirstDate\":\"1976-01-01 00:00:00\",\"LastDate\":\"1997-06-16 23:59:59\",\"Saldo\":\"73089\",\"Sentences\":\"1646688\",\"Size\":\"24331936\",\"Updated\":\"2016-03-15\"}},\"ROMI\":{\"attrs\":{\"a\":[],\"p\":[\"word\",\"pos\",\"msd\",\"lemma\",\"lex\",\"saldo\",\"prefix\",\"suffix\",\"ref\",\"dephead\",\"deprel\"],\"s\":[\"sentence\",\"sentence_id\",\"text\",\"text_title\",\"text_datefrom\",\"text_dateto\",\"text_timefrom\",\"text_timeto\",\"paragraph\",\"paragraph_n\",\"text_author\",\"text_year\"]},\"info\":{\"Charset\":\"utf8\",\"FirstDate\":\"1976-01-01 00:00:00\",\"LastDate\":\"1977-12-31 23:59:59\",\"Saldo\":\"73089\",\"Sentences\":\"499030\",\"Size\":\"6579220\",\"Updated\":\"2015-12-18\"}}},\"time\":4.41E-4,\"total_sentences\":2145718,\"total_size\":30911156}";
    private String jsonString = "{\"corpora\":{\"PAROLE\":{\"attrs\":{\"p\":[\"word\",\"lemma\",\"pos\",\"msd\"],\"s\":[\"sentence\",\"sentence_n\",\"text\",\"text_title\",\"text_date\",\"text_datefrom\",\"text_dateto\",\"text_timefrom\",\"text_timeto\"],\"a\": []},\"info\": {\"Name\": \"PAROLE\",\"Size\": \"294397\",\"Charset\": \"utf8\"}}},\"total_size\": 294397,\"total_sentences\": 0,\"time\": 0.0035653114318847656}";
    private static SRUServerConfig config;
    private static EndpointDescription sed;
    private static ReadExternalPropFiles read;
    private static KorpEndpointSearchEngine kese;
    private static ServletTester tester;
    private static ServletHolder holder;
    private static HashMap<String, String> params;
    private static Properties prop = new Properties();

    @BeforeClass
    public static void getPropertiesFromConfig() throws SRUConfigException, IOException {
        System.out.println("se.gu.spraakbanken.fcs.endpoint.korp.KorpEndpointSearchEngineTest.getPropertiesFromConfig()");
        try {
            prop = ReadExternalPropFiles.getPropertyFile(System.getProperty("user.dir") + "/target/test-classes/se/gu/spraakbanken/fcs/endpoint/korp/config-test.properties");
            //prop=ReadExternalPropFiles.getPropertyFile("/tmp/config-test.properties");

            assertEquals("localhost", prop.getProperty("serverUrl"));
            assertEquals("8080", prop.getProperty("serverPort"));
            assertEquals("True", prop.getProperty("useServerPort"));

            assertEquals("pcdelgratta.ilc.cnr.it", prop.getProperty("korpUrl"));
            assertEquals("90", prop.getProperty("korpPort"));
            assertEquals("True", prop.getProperty("useKorpPort"));

            assertEquals("True", prop.getProperty("isUD"));

        } catch (MalformedURLException mue) {
            throw new SRUConfigException("Malformed URL");
        }

    }

    @BeforeClass
    public static void parseEndpointDescription() throws SRUConfigException {
        try {;
            sed = SimpleEndpointDescriptionParser.parse(new File("target/test-classes/se/gu/spraakbanken/fcs/endpoint/korp/endpoint-description-test.xml").toURI().toURL());
            //sed = SimpleEndpointDescriptionParser.parse(new File("target/test-classes/se/gu/spraakbanken/fcs/endpoint/korp/endpoint-description_orig.xml").toURI().toURL());
            assertEquals("http://clarin.eu/fcs/capability/basic-search", sed.getCapabilities().get(0).toString());
            assertEquals("http://clarin.eu/fcs/capability/advanced-search", sed.getCapabilities().get(1).toString());
        } catch (MalformedURLException mue) {
            throw new SRUConfigException("Malformed URL");
        }

        tester = new ServletTester();
        //tester.setContextPath("/");

        tester.setContextPath("http://localhost:8082/sru-server");
        tester.setResourceBase("src/main/webapp");
        tester.setClassLoader(SRUServerServlet.class.getClassLoader());
        holder = tester.addServlet(SRUServerServlet.class, "/sru");
        params = new HashMap<String, String>();
        params.put(SRUServerConfig.SRU_TRANSPORT, "http");
        params.put(SRUServerConfig.SRU_HOST, "pcdelgratta.ilc.cnr.it");
        params.put(SRUServerConfig.SRU_PORT, "8082");
        params.put(SRUServerConfig.SRU_DATABASE, "sru-server");
        params.put(SRUServerServlet.SRU_SERVER_CONFIG_LOCATION_PARAM, "src/main/webapp/WEB-INF/sru-server-config.xml");
        params.put("eu.clarin.sru.server.propertyFile", "/tmp/config-test.properties");

        // try {
        //     String baseUrl = tester.createSocketConnector(true);
        // } catch (ServletException e) {
        //     throw new SRUConfigException("Failed to set context attribute.");
        // } catch (Exception e) {
        //     throw new SRUConfigException("Failed to create socket connector.");
        // }
        try {
            //tester.setAttribute(SRUServerServlet.SRU_SERVER_CONFIG_LOCATION_PARAM, "src/main/webapp/WEB-INF/sru-server-config.xml");
            System.out.println("tester.getAttribute():" + tester.getAttribute(SRUServerServlet.SRU_SERVER_CONFIG_LOCATION_PARAM));
            //System.out.println(holder.getServlet().getServletConfig().getInitParameter(SRUServerServlet.SRU_SERVER_CONFIG_LOCATION_PARAM)); 
            tester.start();

        } catch (Exception e) {
            throw new SRUConfigException("Failed to start servlet.");
        }
    }

    @Test
    public void doInit() throws SRUConfigException, ServletException {
        URL url;
        System.out.println("se.gu.spraakbanken.fcs.endpoint.korp.KorpEndpointSearchEngineTest.doInit()");
        try {
            url
                    = //SRUServerServlet.class.getClassLoader().getResource("META-INF/sru-server-config.xml");
                    new File("src/main/webapp/WEB-INF/sru-server-config.xml").toURI().toURL();

        } catch (MalformedURLException mue) {
            throw new SRUConfigException("Malformed URL");
        }
        if (url == null) {
            throw new ServletException("not found, url == null");
        }
        // other runtime configuration, usually obtained from Servlet context

        config = SRUServerConfig.parse(params, url);
        kese = new KorpEndpointSearchEngine();
        System.out.println("config.getBaseUrl() " + config.getBaseUrl());
        System.out.println("config.getDatabase() " + config.getDatabase());

        //System.out.println(holder.getServlet().getServletInfo());
        kese.doInit(config, new SRUQueryParserRegistry.Builder().register(new FCSQueryParser()), params);
        System.out.println("****kese.getCorporaInfo()****" + kese.getCorporaInfo());
        System.out.println("****kese.getCorporaInfo()****" + kese.getCorporaInfo().getCorpora());
        System.out.println("****kese.getCorporaInfo()****" + kese.getCorporaInfo());
        assertNotNull(kese.getCorporaInfo());
        assertNotNull(kese.getCorporaInfo().getTime());
        

        for (String key : kese.getCorporaInfo().getCorpora().keySet()) {
             
             Corpus c = kese.getCorporaInfo().getCorpora().get(key);
             System.out.println("Corpus with key " + key +" has " +c.getAttrs().getP().toString());
        }

      
    }

    @Test
    public void getCapabilitiesFromDescription() throws SRUConfigException {
        assertEquals("http://clarin.eu/fcs/capability/basic-search", sed.getCapabilities().get(0).toString());
        assertEquals("http://clarin.eu/fcs/capability/advanced-search", sed.getCapabilities().get(1).toString());
    }

    @Test
    public void getDataViewsFromDescription() throws SRUConfigException {
        System.out.println(sed.getSupportedDataViews());
        assertEquals("hits", sed.getSupportedDataViews().get(0).getIdentifier());
        assertEquals("SEND_BY_DEFAULT", sed.getSupportedDataViews().get(0).getDeliveryPolicy().toString());
        assertEquals("application/x-clarin-fcs-adv+xml", sed.getSupportedDataViews().get(1).getMimeType());
        assertEquals("application/x-cmdi+xml", sed.getSupportedDataViews().get(2).getMimeType());
        assertEquals("NEED_TO_REQUEST", sed.getSupportedDataViews().get(2).getDeliveryPolicy().toString());
    }

    @Test
    public void getLayersFromDescription() throws SRUConfigException {
        System.out.println(sed.getSupportedLayers());
        assertEquals("http://ilc4clarin.ilc.cnr.it/services/fcs/layer/word", sed.getSupportedLayers().get(0).getResultId().toString());
        assertEquals("lemma", sed.getSupportedLayers().get(1).getType().toString());
    }

    @Test
    public void listResourcesFromPid() throws SRUException {
        String pid = "hdl:20.500.11752/corpora";//hdl:20.500.11752/corpora";
        List<ResourceInfo> riList = sed.getResourceList(pid);
        for (ResourceInfo ri : riList) {
            if (ri.hasSubResources()) {
                List<ResourceInfo> risList = ri.getSubResources();
                for (ResourceInfo sri : risList) {
                    System.out.println("(Sub)ResourceInfo with PID " + sri.getPid() + " has title " + sri.getTitle());
                }
            } else {
                System.out.println("ResourceInfo " + ri.getPid() + " has no subserouces");
            }

        }
    }
    @Test
    public void getResourcesFromDescription() throws SRUException {
        //List<ResourceInfo> riList = sed.getResourceList("hdl:20.500.11752/parole"); //hdl:10794/sbmoderna
        List<ResourceInfo> riList = sed.getResourceList("hdl:20.500.11752/corpora");
        if (riList == null) {
            
            System.out.println("ResourceInfo list is null ");
        } else {
            System.out.println("ResourceInfo list is not null and " + riList.get(0).getTitle());
            System.out.println("ResourceInfo list is not null and " + riList.toString());
        }
        assertEquals("hits", riList.get(0).getAvailableDataViews().get(0).getIdentifier());
        assertEquals("SEND_BY_DEFAULT", riList.get(0).getAvailableDataViews().get(0).getDeliveryPolicy().toString());
        assertEquals("application/x-clarin-fcs-hits+xml", riList.get(0).getAvailableDataViews().get(0).getMimeType());
        assertEquals("http://ilc4clarin.ilc.cnr.it/corpora/parole", riList.get(0).getLandingPageURI());
        assertTrue(riList.get(0).hasAvailableLayers());
        assertEquals("word", riList.get(0).getAvailableLayers().get(0).getId());
        assertEquals("text", riList.get(0).getAvailableLayers().get(0).getType());
        assertNull(riList.get(0).getAvailableLayers().get(0).getQualifier());
        assertEquals("ita", riList.get(0).getLanguages().get(0));
        assertFalse(riList.get(0).hasSubResources());
    }

    @Test
    public void convertCQL() throws SRUException {
        Map<String, String> params = new HashMap<String, String>();
        //params.put("query", "text = 'användning' AND text = 'begränsad'");
        final String query = "text = 'användningen är begränsad'";
        final String res = "[word = 'användningen'][word = 'är'][word = 'begränsad']";
        params.put("query", query);
        SRUDiagnosticList diagnostics = new Diagnostic();
        final String resActual = FCSToCQPConverter.makeCQPFromCQL((new CQLQueryParser()).parseQuery(SRUVersion.VERSION_2_0, params, diagnostics));

        System.out.println(resActual);
        assertEquals(res, resActual);
    }

    @Test
    public void convertFCSSimple() throws SRUException {
        Map<String, String> params = new HashMap<String, String>();
        final String query = "[word = 'användning' & pos = 'NOUN']";
        final String res = "[word = 'användning' & pos = 'NN']";
        params.put("query", query);
        //params.put("query", "[text = 'användning']");
        SRUDiagnosticList diagnostics = new Diagnostic();
        final String resActual = FCSToCQPConverter.makeCQPFromFCS((new FCSQueryParser()).parseQuery(SRUVersion.VERSION_2_0, params, diagnostics));

        System.out.println(resActual);
        assertEquals(res, resActual);
    }

    @Test
    public void convertFCSLemma() throws SRUException {
        Map<String, String> params = new HashMap<String, String>();
        final String query = "[lemma = 'Kabul' & pos = 'PROPN']";
        final String res = "[lemma contains 'Kabul' & pos = 'PM']";
        params.put("query", query);
        SRUDiagnosticList diagnostics = new Diagnostic();
        final String resActual = FCSToCQPConverter.makeCQPFromFCS((new FCSQueryParser()).parseQuery(SRUVersion.VERSION_2_0, params, diagnostics));

        System.out.println(resActual);
        assertEquals(res, resActual);
    }

    @Test
    public void convertFCSNot() throws SRUException {
        Map<String, String> params = new HashMap<String, String>();
        final String query = "[word = 'användning' & pos != 'NOUN']";
        final String res = "[word = 'användning' & pos != 'NN']";
        params.put("query", query);
        //params.put("query", "[text = 'användning']");
        SRUDiagnosticList diagnostics = new Diagnostic();
        final String resActual = FCSToCQPConverter.makeCQPFromFCS((new FCSQueryParser()).parseQuery(SRUVersion.VERSION_2_0, params, diagnostics));

        System.out.println(resActual);
        assertEquals(res, resActual);
    }

    @Test
    public void convertFCSWildcard() throws SRUException {
        Map<String, String> params = new HashMap<String, String>();
        final String query = "[word = 'användning' & pos = 'NOUN'] [] [pos = 'ADJ'] ";
        final String res = "[word = 'användning' & pos = 'NN'] [] [pos = '(JJ|PC|RO)'] ";
        params.put("query", query);
        SRUDiagnosticList diagnostics = new Diagnostic();
        final String resActual = FCSToCQPConverter.makeCQPFromFCS((new FCSQueryParser()).parseQuery(SRUVersion.VERSION_2_0, params, diagnostics));

        System.out.println(resActual);
        assertEquals(res, resActual);
    }

    @Test
    public void convertFCSRegexCaseInsensitive() throws SRUException {
        Map<String, String> params = new HashMap<String, String>();
        final String query = "[word = 'användning'/c & pos = 'NOUN'] ";
        final String res = "[word = 'användning' %c & pos = 'NN'] ";
        params.put("query", query);
        SRUDiagnosticList diagnostics = new Diagnostic();
        final String resActual = FCSToCQPConverter.makeCQPFromFCS((new FCSQueryParser()).parseQuery(SRUVersion.VERSION_2_0, params, diagnostics));

        System.out.println(resActual);
        assertEquals(res.trim(), resActual.trim());
    }

    @Test
    public void convertFCSRegexIgnoreDiacritics() throws SRUException {
        Map<String, String> params = new HashMap<String, String>();
        final String query = "[word = 'användning'/d & pos = 'NOUN'] ";
        final String res = "[word = 'användning' %d & pos = 'NN'] ";
        params.put("query", query);
        SRUDiagnosticList diagnostics = new Diagnostic();
        final String resActual = FCSToCQPConverter.makeCQPFromFCS((new FCSQueryParser()).parseQuery(SRUVersion.VERSION_2_0, params, diagnostics));

        System.out.println(resActual);
        assertEquals(res.trim(), resActual.trim());
    }

    @Test
    //@Ignore
    public void convertFCSRegexLiteral() throws SRUException {
        Map<String, String> params = new HashMap<String, String>();
        final String query = "[word = '?'/l & pos = 'PUNCT'] ";
        final String res = "[word = '?' %l & pos = '(MAD|MID|PAD)'] ";
        params.put("query", query);
        SRUDiagnosticList diagnostics = new Diagnostic();
        final String resActual = FCSToCQPConverter.makeCQPFromFCS((new FCSQueryParser()).parseQuery(SRUVersion.VERSION_2_0, params, diagnostics));

        System.out.println(resActual);
        // This fails right now since you get d too!
        assertEquals(res.trim(), resActual.trim());
    }

    @Test
    public void convertFCSOccurs() throws SRUException {
        Map<String, String> params = new HashMap<String, String>();
        final String query = "[word = 'användning' & pos = 'NOUN'] []{1,3} [pos = 'ADJ'] ";
        final String res = "[word = 'användning' & pos = 'NN'] []{1,3} [pos = '(JJ|PC|RO)'] ";
        params.put("query", query);
        SRUDiagnosticList diagnostics = new Diagnostic();
        final String resActual = FCSToCQPConverter.makeCQPFromFCS((new FCSQueryParser()).parseQuery(SRUVersion.VERSION_2_0, params, diagnostics));

        System.out.println(resActual);
        assertEquals(res, resActual);
    }

    @Test
    public void convertFCSOccursExact() throws SRUException {
        Map<String, String> params = new HashMap<String, String>();
        final String query = "[word = 'användning' & pos = 'NOUN'] []{3} [pos = 'ADJ'] ";
        final String res = "[word = 'användning' & pos = 'NN'] []{3} [pos = '(JJ|PC|RO)'] ";
        params.put("query", query);
        SRUDiagnosticList diagnostics = new Diagnostic();
        final String resActual = FCSToCQPConverter.makeCQPFromFCS((new FCSQueryParser()).parseQuery(SRUVersion.VERSION_2_0, params, diagnostics));

        System.out.println(resActual);
        assertEquals(res, resActual);
    }

    @Test
    public void search1() throws SRUException, SRUConfigException, XMLStreamException {
        SRUDiagnosticList diagnostics = new Diagnostic();
        kese.doInit(config, new SRUQueryParserRegistry.Builder().register(new FCSQueryParser()), params);
        //SRURequest request = new SRURequestImpl(config, queryParsers, new HttpServletRequestWrapper());
        //SRUSearchResultSet ssrs = kese.search(config, request, diagnostics);
        Properties prop = kese.getKeseProp();
        CorporaInfo openCorporaInfo = kese.getCorporaInfo();
        final String query = "[word = 'trasformato'][pos = 'VERB']";
        final String cqpQuery = "[word = \"trasformato\"]"; //[pos = 'VERB']";
        //params
        //Query queryRes = kese.makeQuery(cqpQuery, openCorporaInfo, 0, 25);
        Query queryRes = kese.makeIlc4ClarinQuery(prop, cqpQuery, openCorporaInfo, 1, 250);
        KorpSRUSearchResultSet kssrs = new KorpSRUSearchResultSet(config, diagnostics, queryRes, query, openCorporaInfo);
        StringWriter sw = new StringWriter();
        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();
        XMLStreamWriter xmlStreamWriter = xmlOutputFactory.createXMLStreamWriter(sw);
        try {
            System.out.println("getCurrentRecordCursor 0: " + kssrs.getCurrentRecordCursor());
            if (kssrs.nextRecord()) {
                kssrs.writeRecord(xmlStreamWriter);
                System.out.println("search1: " + sw.toString());
            }
            xmlStreamWriter.flush();
            xmlStreamWriter.close();
        } catch (Exception ex) {
            xmlStreamWriter.close();
        }

        System.out.println("getHits: " + queryRes.getHits());
        System.out.println("getTotalRecordCount: " + kssrs.getTotalRecordCount());
        System.out.println("getRecordCount: " + kssrs.getRecordCount());
        System.out.println("getCurrentRecordCursor 1: " + kssrs.getCurrentRecordCursor());
        assertNotNull(sw.toString());
        //assertEquals(res, resActual);
    }

    @AfterClass
    public static void cleanupServletContainer() throws Exception {
        tester.stop();
    }

    public class Diagnostic implements SRUDiagnosticList {

        @Override
        public void addDiagnostic(String uri, String details, String message) {
        }
    }

    public static void main(String[] args) {
    }
}
