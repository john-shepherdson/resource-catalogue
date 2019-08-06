package eu.einfracentral.registry.controller;

import eu.einfracentral.domain.InfraService;
import eu.einfracentral.domain.ServiceMetadata;
import eu.einfracentral.registry.service.InfraServiceService;
import eu.einfracentral.utils.FacetFilterUtils;
import eu.openminted.registry.core.domain.FacetFilter;
import eu.openminted.registry.core.domain.Paging;
import eu.openminted.registry.core.exception.ResourceNotFoundException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("infraService")
//@ApiIgnore
@Api(value = "Get Information about a Service")
public class InfraServiceController {

    private static final Logger logger = LogManager.getLogger(InfraServiceController.class.getName());
    private InfraServiceService<InfraService, InfraService> infraService;

    @Autowired
    InfraServiceController(InfraServiceService<InfraService, InfraService> service) {
        this.infraService = service;
    }

    @RequestMapping(path = {"{id}", "{id}/{version}"}, method = RequestMethod.DELETE, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InfraService> delete(@PathVariable("id") String id, @PathVariable Optional<String> version, @ApiIgnore Authentication authentication) throws ResourceNotFoundException {
        InfraService service;
        if (version.isPresent())
            service = infraService.get(id, version.get());
        else
            service = infraService.get(id);
        if (service == null) {
            return new ResponseEntity<>(HttpStatus.GONE);
        }
        infraService.delete(service);
        logger.info("User " + authentication.getName() + " deleted InfraService " + service.getName() + " with id: " + service.getId());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @RequestMapping(path = "delete/all", method = RequestMethod.DELETE, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InfraService> deleteAll(@ApiIgnore Authentication authentication) throws ResourceNotFoundException {
        FacetFilter ff = new FacetFilter();
        ff.setQuantity(10000);
        List<InfraService> services = infraService.getAll(ff, null).getResults();
        for (InfraService service : services) {
            logger.info(String.format("Deleting service with name: %s", service.getName()));
            infraService.delete(service);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @RequestMapping(path = {"updateFields/all"}, method = RequestMethod.PATCH, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<List<InfraService>> updateFields(InfraService service, Authentication authentication) {
        return new ResponseEntity<>(infraService.eInfraCentralUpdate(service), HttpStatus.OK);
    }


    @RequestMapping(path = "{id}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<InfraService> get(@PathVariable("id") String id, @ApiIgnore Authentication auth) {
        return new ResponseEntity<>(infraService.get(id), HttpStatus.OK);
    }

    @RequestMapping(path = "{id}/{version}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<InfraService> get(@PathVariable("id") String id, @PathVariable("version") String version,
                                            Authentication auth) {
        InfraService ret = infraService.get(id, version);
        return new ResponseEntity<>(ret, ret != null ? HttpStatus.OK : HttpStatus.NOT_FOUND);
    }

    @RequestMapping(method = RequestMethod.POST, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InfraService> add(@RequestBody InfraService service, Authentication authentication) {
        ResponseEntity<InfraService> ret = new ResponseEntity<>(infraService.add(service, authentication), HttpStatus.OK);
        logger.info("User " + authentication.getName() + " added InfraService " + service.getName() + " with id: " + service.getId() + " and version: " + service.getVersion());
        logger.info(" Service Providers: " + service.getProviders());
        return ret;
    }

    @RequestMapping(method = RequestMethod.PUT, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InfraService> update(@RequestBody InfraService service, @ApiIgnore Authentication authentication) throws ResourceNotFoundException {
        ResponseEntity<InfraService> ret = new ResponseEntity<>(infraService.update(service, authentication), HttpStatus.OK);
        logger.info("User " + authentication.getName() + " updated InfraService " + service.getName() + " with id: " + service.getId());
        return ret;
    }

    @RequestMapping(path = "validate", method = RequestMethod.POST, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<Boolean> validate(@RequestBody InfraService service, @ApiIgnore Authentication auth) {
        ResponseEntity<Boolean> ret = ResponseEntity.ok(infraService.validate(service));
        logger.info("Validating InfraService " + service.getName());
        return ret;
    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "query", value = "Keyword to refine the search", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "from", value = "Starting index in the result set", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "quantity", value = "Quantity to be fetched", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "order", value = "asc / desc", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "orderField", value = "Order field", dataType = "string", paramType = "query")
    })
    @RequestMapping(path = "all", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<Paging<InfraService>> getAll(@ApiIgnore @RequestParam MultiValueMap<String, Object> allRequestParams, @ApiIgnore Authentication authentication) {
        FacetFilter ff = FacetFilterUtils.createMultiFacetFilter(allRequestParams);
        return ResponseEntity.ok(infraService.getAll(ff, authentication));
    }

    @RequestMapping(path = "by/{field}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<Map<String, List<InfraService>>> getBy(@PathVariable String field, @ApiIgnore Authentication auth) throws NoSuchFieldException {
        return ResponseEntity.ok(infraService.getBy(field));
    }

    @RequestMapping(path = "publish/{id}/{version}", method = RequestMethod.PATCH, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InfraService> setActive(@PathVariable String id, @PathVariable String version,
                                                  @RequestParam Boolean active, @RequestParam Boolean latest,
                                                  @ApiIgnore Authentication auth) throws ResourceNotFoundException {
        InfraService service = infraService.get(id, version);
        service.setActive(active);
        service.setLatest(latest);
        ServiceMetadata sm = service.getServiceMetadata();
        sm.setModifiedBy("system");
        sm.setModifiedAt(String.valueOf(System.currentTimeMillis()));
        service.setServiceMetadata(sm);
        if (active) {
            logger.info("User " + auth.getName() + " set InfraService " + service.getName() + " with id: " + service.getId() + " to active");
        } else {
            logger.info("User " + auth.getName() + " set InfraService " + service.getName() + " with id: " + service.getId() + " to inactive");
        }
        return ResponseEntity.ok(infraService.update(service, auth));
    }

}
