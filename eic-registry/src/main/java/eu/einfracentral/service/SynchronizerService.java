package eu.einfracentral.service;

import eu.einfracentral.domain.InfraService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

@PropertySource(value = {"classpath:application.properties", "classpath:registry.properties"})
@Service
public class SynchronizerService {

    private static final Logger logger = LogManager.getLogger(SynchronizerService.class);

    private RestTemplate restTemplate;
    private HttpHeaders headers;
    private URI url;
    private boolean active = false;
    private String host;
    private String token;

    // TODO: load token from file, to enable changing it on the fly

    @Autowired
    public SynchronizerService(@Value("${sync.host:localhost}") String host, @Value("${sync.token:noToken}") String token) throws URISyntaxException {
        this.host = host;
        this.token = token;
        restTemplate = new RestTemplate();
        headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        url = new URI(host + "/service");
        if (!token.equals("noToken")) {
            active = true;
        }
    }

    @Async
    public void syncAdd(InfraService infraService) {
        if (active) {
            HttpEntity<InfraService> request = new HttpEntity<>(infraService, headers);
            logger.info(String.format("Posting service with id: %s - Host: %s", infraService.getId(), host));
            restTemplate.postForObject(url.normalize(), request, InfraService.class);
        }
    }

    @Async
    public void syncUpdate(InfraService infraService) {
        if (active) {
            HttpEntity<InfraService> request = new HttpEntity<>(infraService, headers);
            logger.info(String.format("Updating service with id: %s - Host: %s", infraService.getId(), host));
            restTemplate.put(url.normalize().toString(), request, InfraService.class);
        }
    }

    @Async
    public void syncDelete(InfraService infraService) {
        if (active) {
            HttpEntity<String> request = new HttpEntity<>(infraService.getId(), headers);
            // FIXME
            try {
                logger.info(String.format("Deleting service with id: %s - Host: %s", infraService.getId(), host));
                restTemplate.delete(new URI(host + "/infraService").normalize().toString(), request, String.class);
            } catch (URISyntaxException e) {
                logger.error("Could not execute syncDelete method", e);
            }
        }
    }
}
