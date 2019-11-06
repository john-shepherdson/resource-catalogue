package eu.einfracentral.domain;

import io.swagger.annotations.ApiModelProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.net.URL;
import java.util.List;

@XmlType
@XmlRootElement(namespace = "http://einfracentral.eu")
public class ServiceOption implements Identifiable {


    // Option Basic Information
    /**
     * Identifier of the service option.
     */
    @XmlElement(required = true)
    @ApiModelProperty(position = 1, example = "(required on PUT only)", required = true)
    private String id;

    /**
     * Name of the service option.
     */
    @XmlElement(required = true)
    @ApiModelProperty(position = 2, example = "String (required)", required = true)
    private String name;

    /**
     * Webpage with information about the service option.
     */
    @XmlElement(required = true)
    @ApiModelProperty(position = 3, example = "URL (required)", required = true)
    private URL url;

    /**
     * The description of the service option.
     */
    @XmlElement(required = true)
    @ApiModelProperty(position = 4, example = "String (required)", required = true)
    private String description;

    /**
     * Link to the logo/visual identity of the service provider.
     */
    @XmlElement
    @ApiModelProperty(position = 5, example = "URL (optional)")
    private URL logo;


    // Option Contact Information
    /**
     * List of option's contact persons info.
     */
    @XmlElementWrapper(name = "contacts", required = true)
    @XmlElement(name = "contact")
    @ApiModelProperty(position = 6, required = true)
    private List<Contact> contacts;


    // Option Other Information
    /**
     * List of option's attributes.
     */
    @XmlElementWrapper(name = "attributes")
    @XmlElement(name = "attribute")
    @ApiModelProperty(position = 7, dataType = "List", example = "String[] (optional)")
    private List<String> attributes;


    public ServiceOption() {
    }

    @Override
    public String toString() {
        return "ServiceOption{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", url=" + url +
                ", description='" + description + '\'' +
                ", logo=" + logo +
                ", contacts=" + contacts +
                ", attributes=" + attributes +
                '}';
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public URL getLogo() {
        return logo;
    }

    public void setLogo(URL logo) {
        this.logo = logo;
    }

    public List<Contact> getContacts() {
        return contacts;
    }

    public void setContacts(List<Contact> contacts) {
        this.contacts = contacts;
    }

    public List<String> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<String> attributes) {
        this.attributes = attributes;
    }
}