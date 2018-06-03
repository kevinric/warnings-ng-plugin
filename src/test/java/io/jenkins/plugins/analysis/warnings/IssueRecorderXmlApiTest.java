package io.jenkins.plugins.analysis.warnings;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.gargoylesoftware.htmlunit.xml.XmlPage;

import static io.jenkins.plugins.analysis.core.model.Assertions.*;
import io.jenkins.plugins.analysis.core.steps.IssuesRecorder;
import io.jenkins.plugins.analysis.core.steps.ToolConfiguration;

import hudson.model.FreeStyleProject;

/**
 * Integration tests of the warnings plug-in in freestyle jobs. Tests the new recorder {@link IssuesRecorder}. Subclass
 * to test the xml api.
 *
 * @author Manuel Hampp
 */
public class IssueRecorderXmlApiTest extends IssuesRecorderITest {

    /**
     * Compares the basic xml api (without parameters) against a control result.
     */
    @Test
    public void assertXmlApiMatchesExpected() {
        try {
            // setup project
            FreeStyleProject project = createJobWithWorkspaceFile("checkstyleregextest.xml");
            enableWarningsWithCheckstyle(project);
            String buildNumber = String.valueOf(project.getNextBuildNumber());
            scheduleBuild(project);

            // get xml result from API
            XmlPage page = j.createWebClient().goToXml(project.getUrl() + buildNumber + "/checkstyleResult/api/xml");
            Document test = page.getXmlDocument();

            // remove not comparable nodes
            test = removeRunSpecificXmlNodes(test);

            // get control document
            Document control = loadControlDocumentFromFile("checkstyleregextest_output.xml");

            // compare documents
            Diff diff = XMLUnit.compareXML(control, test);

            // assert that document recieved by api is the same as expected
            assertThat(diff.identical());

        }
        catch (IOException | SAXException e) {
            e.printStackTrace();
        }

    }

    /**
     * Tests the xpath navigation within the xml api.
     */
    @Test
    public void assertXmlApiWithXPathNavigationMatchesExpected() {
        try {
            // setup project
            FreeStyleProject project = createJobWithWorkspaceFile("checkstyleregextest.xml");
            enableWarningsWithCheckstyle(project);
            String buildNumber = String.valueOf(project.getNextBuildNumber());
            scheduleBuild(project);

            // get xml result from API
            XmlPage page = j.createWebClient()
                    .goToXml(project.getUrl() + buildNumber + "/checkstyleResult/api/xml?xpath=/*/overallResult");
            Document test = page.getXmlDocument();

            // assert that root is the xpath aimed element
            assertThat(test.getDocumentElement().getTagName()).isEqualTo("overallResult");
        }
        catch (IOException | SAXException e) {
            e.printStackTrace();
        }
    }

    /**
     * Tests the depth parameter within the xml api.
     */
    @Test
    public void assertXmlApiWithDepthContainsDeepElements() {
        try {
            // setup project
            FreeStyleProject project = createJobWithWorkspaceFile("checkstyleregextest.xml");
            enableWarningsWithCheckstyle(project);
            String buildNumber = String.valueOf(project.getNextBuildNumber());
            scheduleBuild(project);

            // get xml result from API
            XmlPage page = j.createWebClient()
                    .goToXml(project.getUrl() + buildNumber + "/checkstyleResult/api/xml?depth=1");
            Document test = page.getXmlDocument();

            // navigate to deep level element
            XPath xp = XPathFactory.newInstance().newXPath();
            Node deepLevelElement = (Node) xp.compile("//analysisResult//owner//action//cause//*")
                    .evaluate(test, XPathConstants.NODE);

            // assert element exists, that is not contained in a call without depth parameter
            assertThat(deepLevelElement.getNodeName()).isEqualTo("shortDescription");
        }
        catch (IOException | SAXException | XPathExpressionException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads a control document from the given file.
     *
     * @param filename
     *         name from the file that is placed inside the 'test/resources/io/jenkins/plugins/analysis/warnings/'
     *         folder.
     *
     * @return Document that was extracted from the file.
     */
    private Document loadControlDocumentFromFile(final String filename) {
        // create document builder
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        Document document = null;
        try {
            builder = factory.newDocumentBuilder();
            // get document from file
            document = builder.parse(new File("src/test/resources/io/jenkins/plugins/analysis/warnings/" + filename));
        }
        catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }

        // remove not comparable nodes
        document = removeRunSpecificXmlNodes(document);

        return document;

    }

    /**
     * Removes run specific nodes from document. Some infoMessage Elements (containing a temporary path) and the owner
     * node are removed.
     */
    private Document removeRunSpecificXmlNodes(Document doc) {
        XPath xp = XPathFactory.newInstance().newXPath();
        try {
            // remove nodes with run specific tmp-folder name
            NodeList infoMessageNodesWithTmpFolderPath = (NodeList) xp.compile(
                    "//analysisResult//infoMessage[contains(text(), '/tmp/jenkinsTests.tmp/')]")
                    .evaluate(doc, XPathConstants.NODESET);

            // delete nodes
            for (int i = infoMessageNodesWithTmpFolderPath.getLength() - 1; i >= 0; i--) {
                infoMessageNodesWithTmpFolderPath.item(i)
                        .getParentNode()
                        .removeChild(infoMessageNodesWithTmpFolderPath.item(i));
            }

            // remove owner node (port could be different
            Node n = (Node) xp.compile("//analysisResult//owner").evaluate(doc, XPathConstants.NODE);
            n.getParentNode().removeChild(n);

        }
        catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        return doc;
    }

    /**
     * Enables the warnings plugin for the specified job. I.e., it registers a new {@link IssuesRecorder } recorder for
     * the job.
     *
     * @param job
     *         the job to register the recorder for
     */
    private void enableWarningsWithCheckstyle(final FreeStyleProject job) {
        IssuesRecorder publisher = new IssuesRecorder();
        publisher.setTools(Collections.singletonList(new ToolConfiguration("**/*issues.txt", new CheckStyle())));
        job.getPublishersList().add(publisher);
    }
}
