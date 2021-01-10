package server;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;

public class Database {

    public String filePath;
    private File file;
    private Document database = null;
    private boolean fileExists = false;

    public Database(String filePath) {
        this.filePath = filePath;
        if (filePath != null) {
            this.file = new File(filePath);
            fileExists = this.file.exists();
        }
        System.out.println("Database File: \"" + this.filePath
                + "\". File Exists: " + fileExists);

        if (fileExists) {
            try {
                DocumentBuilderFactory d = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = d.newDocumentBuilder();

                database = dBuilder.parse(this.file);
                database.getDocumentElement().normalize();
            } catch (Exception ex) {
                System.out.println("Database exception: " + ex.toString());
            }
        }
    }

    public boolean checkLogin(String username, String password) {
        if (fileExists) {
            NodeList nList = database.getElementsByTagName("user");

            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    if (tagValue("username", eElement).equals(username)
                            && tagValue("password", eElement).equals(password)
                            && tagValue("status", eElement).equals("valid")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public String viewUsers() {
        String users = "";

        if (fileExists) {
            NodeList nList = database.getElementsByTagName("user");

            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    users += tagValue("username", eElement)
                        + ":"
                        + tagValue("status", eElement).equals("valid")
                        + "; ";
                }
            }
        }
        return users;
    }

    public boolean blockUser(String username) {
        if (fileExists) {
            NodeList nList = database.getElementsByTagName("user");

            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    if (tagValue("username", eElement).equals(username)) {
                        eElement.getLastChild().setTextContent("invalid");
                        return true;
                    }
                }
            }
        }

        System.out.println("Cannot find user '" + username + "'User Wrong");
        return false;
    }

    public void addUser(String username, String password) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document doc = docBuilder.parse(filePath);

            Node data = doc.getFirstChild();

            Element newuser = doc.createElement("user");
            Element newusername = doc.createElement("username");
            newusername.setTextContent(username);
            Element newpassword = doc.createElement("password");
            newpassword.setTextContent(password);
            Element newstatus = doc.createElement("status");
            newstatus.setTextContent("valid");

            newuser.appendChild(newusername);
            newuser.appendChild(newpassword);
            newuser.appendChild(newstatus);
            data.appendChild(newuser);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filePath));
            transformer.transform(source, result);
        } catch (Exception ex) {
            System.out.println("Database addUser exception: " + ex.toString());
        }
    }

    public boolean userExists(String username) {
        if (fileExists) {
            NodeList nList = database.getElementsByTagName("user");
            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    if (tagValue("username", eElement).equals(username)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static String tagValue(String sTag, Element eElement) {
        NodeList nlList = eElement
                            .getElementsByTagName(sTag)
                            .item(0)
                            .getChildNodes();
        Node nValue = (Node) nlList.item(0);
        return nValue.getNodeValue();
    }
}
