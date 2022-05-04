package eu.einfracentral.service.sync;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ServiceSync extends AbstractSyncService<eu.einfracentral.domain.Service> {

    @Autowired
    public ServiceSync(@Value("${sync.host:}") String host, @Value("${sync.token.filepath:}") String filename) {
        super(host, filename);
    }

    @Override
    protected String getController() {
        return "/service";
    }

}
