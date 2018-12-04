package eu.einfracentral.registry.controller;

import eu.einfracentral.domain.InfraService;
import eu.einfracentral.domain.ServiceMetadata;
import eu.einfracentral.registry.service.InfraServiceService;
import eu.openminted.registry.core.domain.FacetFilter;
import eu.openminted.registry.core.domain.Paging;
import eu.openminted.registry.core.exception.ResourceNotFoundException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("infraService")
@ApiIgnore
@Api(value = "Get Information about a Service")
public class InfraServiceController {

    private static final Logger logger = LogManager.getLogger(InfraServiceController.class.getName());
    private InfraServiceService<InfraService, InfraService> infraService;

    @Autowired
    InfraServiceController(InfraServiceService<InfraService, InfraService> service) {
        this.infraService = service;
    }

    @ApiIgnore
    @RequestMapping(path = {"delete/{id}/", "delete/{id}/{version}/"}, method = RequestMethod.DELETE, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InfraService> delete(@PathVariable("id") String id, @PathVariable Optional<String> version, @ApiIgnore Authentication authentication) throws ResourceNotFoundException {
        InfraService service;
        if (version.isPresent())
            service = infraService.get(id, version.get());
        else
            service = infraService.getLatest(id);
        infraService.delete(service);
        return new ResponseEntity<>(HttpStatus.OK);
    }
    @ApiIgnore
    @RequestMapping(path = "delete/all/", method = RequestMethod.DELETE, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InfraService> deleteAll(@ApiIgnore Authentication authentication) throws ResourceNotFoundException {
        FacetFilter ff = new FacetFilter();
        ff.setQuantity(10000);
        List<InfraService> services = infraService.getAll(ff, null).getResults();
        for (InfraService service : services) {
            logger.info(String.format("Deleting service with name: %s", service.getName()));
            infraService.delete(service);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(path = {"updateFields/all/"}, method = RequestMethod.PATCH, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<List<InfraService>> updateFields(InfraService service, Authentication authentication) {
        return new ResponseEntity<>(infraService.eInfraCentralUpdate(service), HttpStatus.OK);
    }


    @ApiOperation(value = "Get the most current version of a specific infraService providing the infraService ID")
    @RequestMapping(path = "{id}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<InfraService> get(@PathVariable("id") String id, @ApiIgnore Authentication auth) throws ResourceNotFoundException {
        return new ResponseEntity<>(infraService.getLatest(id), HttpStatus.OK);
    }

    @ApiOperation(value = "Get the most current version of a specific infraService providing the infraService ID")
    @RequestMapping(path = "{id}/{version}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<InfraService> get(@PathVariable("id") String id, @PathVariable("version") String version,
                                            Authentication auth) {
        InfraService ret = infraService.get(id, version);
        return new ResponseEntity<>(ret, ret != null ? HttpStatus.OK : HttpStatus.NOT_FOUND);
    }

    @CrossOrigin
    @ApiOperation(value = "Adds the given infraService.")
    @RequestMapping(method = RequestMethod.POST, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InfraService> add(@RequestBody InfraService service, Authentication authentication) {
        return new ResponseEntity<>(infraService.add(service, authentication), HttpStatus.OK);
    }

    @ApiOperation(value = "Updates the infraService assigned the given id with the given infraService, keeping a history of revisions.")
    @RequestMapping(method = RequestMethod.PUT, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InfraService> update(@RequestBody InfraService service, @ApiIgnore Authentication authentication) throws ResourceNotFoundException {
        return new ResponseEntity<>(infraService.update(service, authentication), HttpStatus.OK);
    }

    @ApiOperation(value = "Validates the infraService without actually changing the repository")
    @RequestMapping(path = "validate", method = RequestMethod.POST, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<Boolean> validate(@RequestBody InfraService service, @ApiIgnore Authentication auth) {
        return ResponseEntity.ok(infraService.validate(service));
    }

    @ApiOperation(value = "Filter a list of services based on a set of filters or get a list of all services in the eInfraCentral Catalogue  ")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "query", value = "Keyword to refine the search", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "from", value = "Starting index in the resultset", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "quantity", value = "Quantity of services to be fetched", dataType = "string", paramType = "query")
    })
    @RequestMapping(path = "all", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<Paging<InfraService>> getAll(@ApiIgnore @RequestParam Map<String, Object> allRequestParams, @ApiIgnore Authentication authentication) {
        FacetFilter ff = new FacetFilter();
        ff.setKeyword(allRequestParams.get("query") != null ? (String) allRequestParams.remove("query") : "");
        ff.setFrom(allRequestParams.get("from") != null ? Integer.parseInt((String) allRequestParams.remove("from")) : 0);
        ff.setQuantity(allRequestParams.get("quantity") != null ? Integer.parseInt((String) allRequestParams.remove("quantity")) : 10);
        ff.setFilter(allRequestParams);
        return ResponseEntity.ok(infraService.getAll(ff, authentication));
    }

    @ApiOperation(value = "Get all services in the catalogue organized by an attribute, e.g. get infraService organized in categories ")
    @RequestMapping(path = "by/{field}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<Map<String, List<InfraService>>> getBy(@PathVariable String field, @ApiIgnore Authentication auth) throws NoSuchFieldException {
        return ResponseEntity.ok(infraService.getBy(field));
    }

    @ApiOperation(value = "Set a service active or inactive")
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
        return ResponseEntity.ok(infraService.update(service, auth));
    }

}
