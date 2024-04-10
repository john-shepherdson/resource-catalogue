package gr.uoa.di.madgik.resourcecatalogue.controllers.registry;

import gr.uoa.di.madgik.resourcecatalogue.annotations.Browse;
import gr.uoa.di.madgik.resourcecatalogue.domain.*;
import gr.uoa.di.madgik.resourcecatalogue.dto.Value;
import gr.uoa.di.madgik.resourcecatalogue.utils.FacetFilterUtils;
import gr.uoa.di.madgik.resourcecatalogue.service.InteroperabilityRecordService;
import gr.uoa.di.madgik.resourcecatalogue.service.ResourceInteroperabilityRecordService;
import gr.uoa.di.madgik.resourcecatalogue.service.GenericResourceService;
import gr.uoa.di.madgik.resourcecatalogue.service.SecurityService;
import gr.uoa.di.madgik.registry.domain.FacetFilter;
import gr.uoa.di.madgik.registry.domain.Paging;
import gr.uoa.di.madgik.registry.exception.ResourceNotFoundException;


import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"interoperabilityRecord"})
@Tag(name = "interoperability record")
public class InteroperabilityRecordController {

    private static final Logger logger = LogManager.getLogger(InteroperabilityRecordController.class);
    private final InteroperabilityRecordService<InteroperabilityRecordBundle> interoperabilityRecordService;
    private final ResourceInteroperabilityRecordService<ResourceInteroperabilityRecordBundle> resourceInteroperabilityRecordService;
    private final GenericResourceService genericResourceService;
    private final SecurityService securityService;

    @Autowired
    public InteroperabilityRecordController(InteroperabilityRecordService<InteroperabilityRecordBundle> interoperabilityRecordService,
                                            ResourceInteroperabilityRecordService<ResourceInteroperabilityRecordBundle> resourceInteroperabilityRecordService,
                                            GenericResourceService genericResourceService, SecurityService securityService) {
        this.interoperabilityRecordService = interoperabilityRecordService;
        this.resourceInteroperabilityRecordService = resourceInteroperabilityRecordService;
        this.genericResourceService = genericResourceService;
        this.securityService = securityService;
    }

    @Operation(description = "Creates a new Interoperability Record.")
    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InteroperabilityRecord> add(@RequestBody InteroperabilityRecord interoperabilityRecord, @Parameter(hidden = true) Authentication auth) {
        InteroperabilityRecordBundle ret = this.interoperabilityRecordService.add(new InteroperabilityRecordBundle(interoperabilityRecord), auth);
        logger.info("User '{}' added a new Interoperability Record with id '{}' and title '{}'", auth.getName(), interoperabilityRecord.getId(), interoperabilityRecord.getTitle());
        return new ResponseEntity<>(ret.getInteroperabilityRecord(), HttpStatus.CREATED);
    }

    @Operation(description = "Updates the InteroperabilityRecord with the given id.")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_EPOT') or @securityService.isResourceProviderAdmin(#auth,#interoperabilityRecord)")
    @PutMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<InteroperabilityRecord> update(@RequestBody InteroperabilityRecord interoperabilityRecord,
                                                         @Parameter(hidden = true) Authentication auth) throws ResourceNotFoundException {
        InteroperabilityRecordBundle ret = this.interoperabilityRecordService.update(new InteroperabilityRecordBundle(interoperabilityRecord), auth);
        logger.info("User '{}' updated Interoperability Record with id '{}' and title '{}'", auth.getName(), interoperabilityRecord.getId(), interoperabilityRecord.getTitle());
        return new ResponseEntity<>(ret.getInteroperabilityRecord(), HttpStatus.OK);
    }

    // Deletes the Interoperability Record with the specific ID.
    @DeleteMapping(path = "{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_EPOT') or @securityService.isResourceProviderAdmin(#auth, #id)")
    public ResponseEntity<InteroperabilityRecord> delete(@PathVariable("id") String id, @RequestParam(defaultValue = "${project.catalogue.name}", name = "catalogue_id") String catalogueId,
                                                         @Parameter(hidden = true) Authentication auth) throws ResourceNotFoundException {
        InteroperabilityRecordBundle interoperabilityRecordBundle = interoperabilityRecordService.get(id, catalogueId);
        if (interoperabilityRecordBundle == null) {
            return new ResponseEntity<>(HttpStatus.GONE);
        }
        logger.info("Deleting Interoperability Record: {}", interoperabilityRecordBundle.getId());
        interoperabilityRecordService.delete(interoperabilityRecordBundle);
        logger.info("User '{}' deleted the Interoperability Record with id '{}'", auth.getName(), interoperabilityRecordBundle.getId());
        return new ResponseEntity<>(interoperabilityRecordBundle.getInteroperabilityRecord(), HttpStatus.OK);
    }

    @Operation(description = "Returns the Interoperability Record with the given id.")
    @GetMapping(path = "{id}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    public ResponseEntity<InteroperabilityRecord> getInteroperabilityRecord(@PathVariable("id") String id,
                                                                            @RequestParam(defaultValue = "${project.catalogue.name}", name = "catalogue_id") String catalogueId) {
        InteroperabilityRecord interoperabilityRecord = interoperabilityRecordService.get(id, catalogueId).getInteroperabilityRecord();
        return new ResponseEntity<>(interoperabilityRecord, HttpStatus.OK);
    }

    @GetMapping(path = "bundle/{id}", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_EPOT') or @securityService.isResourceProviderAdmin(#auth, #id)")
    public ResponseEntity<InteroperabilityRecordBundle> getInteroperabilityRecordBundle(@PathVariable("id") String id,
                                                                                        @RequestParam(defaultValue = "${project.catalogue.name}", name = "catalogue_id") String catalogueId,
                                                                                        @Parameter(hidden = true) Authentication auth) {
        return new ResponseEntity<>(interoperabilityRecordService.get(id, catalogueId), HttpStatus.OK);
    }

    @Operation(description = "Get all Interoperability Records")
    @Browse
    @Parameter(name = "suspended", description = "Suspended", content = @Content(schema = @Schema(type = "boolean", defaultValue = "false")))
    @GetMapping(path = "all", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    public ResponseEntity<Paging<InteroperabilityRecord>> getAll(@RequestParam(defaultValue = "all", name = "catalogue_id") String catalogueId,
                                                                 @Parameter(hidden = true) @RequestParam Map<String, Object> allRequestParams,
                                                                 @Parameter(hidden = true) Authentication auth) {
        allRequestParams.putIfAbsent("catalogue_id", catalogueId);
        if (catalogueId != null && catalogueId.equals("all")) {
            allRequestParams.remove("catalogue_id");
        }
        FacetFilter ff = FacetFilterUtils.createFacetFilter(allRequestParams);
        ff.addFilter("published", false);
        ff.addFilter("status", "approved interoperability record");
        Paging<InteroperabilityRecordBundle> interoperabilityRecordPaging = interoperabilityRecordService.getAll(ff, auth);
        List<InteroperabilityRecord> interoperabilityRecords = interoperabilityRecordPaging.getResults().stream().map(InteroperabilityRecordBundle::getInteroperabilityRecord).collect(Collectors.toList());
        return ResponseEntity.ok(new Paging<>(interoperabilityRecordPaging.getTotal(), interoperabilityRecordPaging.getFrom(), interoperabilityRecordPaging.getTo(), interoperabilityRecords, interoperabilityRecordPaging.getFacets()));
    }

    @Operation(description = "Get all Interoperability Record Bundles")
    @Browse
    @Parameter(name = "suspended", description = "Suspended", content = @Content(schema = @Schema(type = "boolean", defaultValue = "false")))
    @GetMapping(path = "bundle/all", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_EPOT')")
    public ResponseEntity<Paging<?>> getAllBundles(@Parameter(hidden = true) @RequestParam MultiValueMap<String, Object> allRequestParams,
                                                   @RequestParam(required = false) Set<String> auditState,
                                                   @RequestParam(defaultValue = "all", name = "catalogue_id") String catalogueId,
                                                   @RequestParam(defaultValue = "all", name = "provider_id") String providerId) {
        FacetFilter ff = interoperabilityRecordService.createFacetFilterForFetchingInteroperabilityRecords(allRequestParams, catalogueId, providerId);
        if (auditState == null) {
            Paging<InteroperabilityRecordBundle> paging = genericResourceService.getResults(ff);
            genericResourceService.sortFacets(paging.getFacets(), "provider_id");
            return ResponseEntity.ok(paging);
        } else {
            return ResponseEntity.ok(interoperabilityRecordService.getAllForAdminWithAuditStates(ff, auditState));
        }
    }

    @PatchMapping(path = "verify/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_EPOT')")
    public ResponseEntity<InteroperabilityRecordBundle> verify(@PathVariable("id") String id, @RequestParam(required = false) Boolean active,
                                                               @RequestParam(required = false) String status, @Parameter(hidden = true) Authentication auth) {
        InteroperabilityRecordBundle interoperabilityRecordBundle = interoperabilityRecordService.verifyResource(id, status, active, auth);
        logger.info("User '{}' verified Interoperability Record with title '{}' [status: {}] [active: {}]", auth, interoperabilityRecordBundle.getInteroperabilityRecord().getTitle(), status, active);
        return new ResponseEntity<>(interoperabilityRecordBundle, HttpStatus.OK);
    }

    @PatchMapping(path = "publish/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_EPOT') or @securityService.providerIsActiveAndUserIsAdmin(#auth, #id)")
    public ResponseEntity<InteroperabilityRecordBundle> setActive(@PathVariable String id, @RequestParam Boolean active, @Parameter(hidden = true) Authentication auth) {
        logger.info("User '{}-{}' attempts to save Interoperability Record with id '{}' as '{}'", User.of(auth).getFullName(), User.of(auth).getEmail(), id, active);
        return ResponseEntity.ok(interoperabilityRecordService.publish(id, active, auth));
    }

    @Operation(description = "Validates the Interoperability Record without actually changing the repository.")
    @PostMapping(path = "validate", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Boolean> validate(@RequestBody InteroperabilityRecord interoperabilityRecord) {
        ResponseEntity<Boolean> ret = ResponseEntity.ok(interoperabilityRecordService.validateInteroperabilityRecord(new InteroperabilityRecordBundle(interoperabilityRecord)));
        return ret;
    }

    @Browse
    @GetMapping(path = "byProvider/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Paging<InteroperabilityRecordBundle>> getInteroperabilityRecordsByProvider(@Parameter(hidden = true) @RequestParam MultiValueMap<String, Object> allRequestParams,
                                                                                                     @RequestParam(defaultValue = "${project.catalogue.name}", name = "catalogue_id") String catalogueId,
                                                                                                     @PathVariable String id, @Parameter(hidden = true) Authentication auth) {
        FacetFilter ff = interoperabilityRecordService.createFacetFilterForFetchingInteroperabilityRecords(allRequestParams, catalogueId, id);
        interoperabilityRecordService.updateFacetFilterConsideringTheAuthorization(ff, auth);
        return ResponseEntity.ok(genericResourceService.getResults(ff));
    }

    @PatchMapping(path = "auditResource/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_EPOT')")
    public ResponseEntity<InteroperabilityRecordBundle> auditResource(@PathVariable("id") String id, @RequestParam("catalogueId") String catalogueId,
                                                                      @RequestParam(required = false) String comment,
                                                                      @RequestParam LoggingInfo.ActionType actionType, @Parameter(hidden = true) Authentication auth) {
        InteroperabilityRecordBundle interoperabilityRecordBundle = interoperabilityRecordService.auditResource(id, catalogueId, comment, actionType, auth);
        logger.info("User '{}-{}' audited Interoperability Record with name '{}' of the '{}' Catalogue - [actionType: {}]", User.of(auth).getFullName(), User.of(auth).getEmail(),
                interoperabilityRecordBundle.getInteroperabilityRecord().getTitle(), interoperabilityRecordBundle.getInteroperabilityRecord().getCatalogueId(), actionType);
        return new ResponseEntity<>(interoperabilityRecordBundle, HttpStatus.OK);
    }

    @GetMapping(path = {"loggingInfoHistory/{id}"}, produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Paging<LoggingInfo>> loggingInfoHistory(@PathVariable String id,
                                                                  @RequestParam(defaultValue = "${project.catalogue.name}", name = "catalogue_id") String catalogueId) {
        Paging<LoggingInfo> loggingInfoHistory = this.interoperabilityRecordService.getLoggingInfoHistory(id, catalogueId);
        return ResponseEntity.ok(loggingInfoHistory);
    }

    @Operation(description = "Returns the Related Resources of a specific Interoperability Record given its id.")
    @GetMapping(path = {"relatedResources/{id}"}, produces = {MediaType.APPLICATION_JSON_VALUE})
    public List<String> getAllInteroperabilityRecordRelatedResources(@PathVariable String id) {
        List<String> allInteroperabilityRecordRelatedResources = new ArrayList<>();
        FacetFilter ff = new FacetFilter();
        ff.setQuantity(10000);
        ff.addFilter("published", false);
        List<ResourceInteroperabilityRecordBundle> allResourceInteroperabilityRecords = resourceInteroperabilityRecordService.getAll(ff, null).getResults();
        for (ResourceInteroperabilityRecordBundle resourceInteroperabilityRecordBundle : allResourceInteroperabilityRecords) {
            if (resourceInteroperabilityRecordBundle.getResourceInteroperabilityRecord().getInteroperabilityRecordIds().contains(id)) {
                allInteroperabilityRecordRelatedResources.add(resourceInteroperabilityRecordBundle.getResourceInteroperabilityRecord().getResourceId());
            }
        }
        return allInteroperabilityRecordRelatedResources;
    }

    // front-end use (Resource Interoperability Record form)
    @GetMapping(path = {"interoperabilityRecordIdToNameMap"}, produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<Value>> interoperabilityRecordIdToNameMap(String catalogueId) {
        List<Value> allInteroperabilityRecords = new ArrayList<>();
        // fetch catalogueId related non-public Interoperability Records
        List<Value> catalogueRelatedInteroperabilityRecords = interoperabilityRecordService
                .getAll(createFacetFilter(catalogueId, false), securityService.getAdminAccess()).getResults()
                .stream().map(InteroperabilityRecordBundle::getInteroperabilityRecord)
                .map(c -> new Value(c.getId(), c.getTitle()))
                .collect(Collectors.toList());
        // fetch non-catalogueId related public Interoperability Records
        List<Value> publicInteroperabilityRecords = interoperabilityRecordService
                .getAll(createFacetFilter(catalogueId, true), securityService.getAdminAccess()).getResults()
                .stream().map(InteroperabilityRecordBundle::getInteroperabilityRecord)
                .filter(c -> !c.getCatalogueId().equals(catalogueId))
                .map(c -> new Value(c.getId(), c.getTitle()))
                .collect(Collectors.toList());

        allInteroperabilityRecords.addAll(catalogueRelatedInteroperabilityRecords);
        allInteroperabilityRecords.addAll(publicInteroperabilityRecords);

        return ResponseEntity.ok(allInteroperabilityRecords);
    }

    private FacetFilter createFacetFilter(String catalogueId, boolean isPublic) {
        FacetFilter ff = new FacetFilter();
        ff.setQuantity(10000);
        ff.addFilter("status", "approved interoperability record");
        ff.addFilter("active", true);
        if (isPublic) {
            ff.addFilter("published", true);
        } else {
            ff.addFilter("catalogue_id", catalogueId);
            ff.addFilter("published", false);
        }
        return ff;
    }

    @PostMapping(path = "addInteroperabilityRecordBundle", produces = {MediaType.APPLICATION_JSON_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InteroperabilityRecordBundle> add(@RequestBody InteroperabilityRecordBundle interoperabilityRecordBundle, Authentication authentication) {
        ResponseEntity<InteroperabilityRecordBundle> ret = new ResponseEntity<>(interoperabilityRecordService.add(interoperabilityRecordBundle, authentication), HttpStatus.OK);
        logger.info("User '{}' added InteroperabilityRecordBundle '{}' with id: {}", authentication, interoperabilityRecordBundle.getInteroperabilityRecord().getTitle(), interoperabilityRecordBundle.getId());
        return ret;
    }

    @PutMapping(path = "updateInteroperabilityRecordBundle", produces = {MediaType.APPLICATION_JSON_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InteroperabilityRecordBundle> update(@RequestBody InteroperabilityRecordBundle interoperabilityRecord, @Parameter(hidden = true) Authentication authentication) throws ResourceNotFoundException {
        InteroperabilityRecordBundle interoperabilityRecordBundle = interoperabilityRecordService.update(interoperabilityRecord, authentication);
        logger.info("User '{}' updated InteroperabilityRecordBundle '{}' with id: {}", authentication, interoperabilityRecordBundle.getInteroperabilityRecord().getTitle(), interoperabilityRecordBundle.getId());
        return new ResponseEntity<>(interoperabilityRecordBundle, HttpStatus.OK);
    }

    // Create a Public InteroperabilityRecord if something went bad during its creation
    @Parameter(hidden = true)
    @PostMapping(path = "createPublicInteroperabilityRecord", produces = {MediaType.APPLICATION_JSON_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InteroperabilityRecordBundle> createPublicInteroperabilityRecord(@RequestBody InteroperabilityRecordBundle interoperabilityRecordBundle, @Parameter(hidden = true) Authentication auth) {
        logger.info("User '{}-{}' attempts to create a Public Interoperability Record from Interoperability Record '{}'-'{}' of the '{}' Catalogue", User.of(auth).getFullName(),
                User.of(auth).getEmail(), interoperabilityRecordBundle.getId(), interoperabilityRecordBundle.getInteroperabilityRecord().getTitle(), interoperabilityRecordBundle.getInteroperabilityRecord().getCatalogueId());
        return ResponseEntity.ok(interoperabilityRecordService.createPublicInteroperabilityRecord(interoperabilityRecordBundle, auth));
    }

    @Operation(description = "Suspends a specific Interoperability Record.")
    @PutMapping(path = "suspend", produces = {MediaType.APPLICATION_JSON_VALUE})
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_EPOT')")
    public InteroperabilityRecordBundle suspendInteroperabilityRecord(@RequestParam String interoperabilityRecordId, @RequestParam String catalogueId, @RequestParam boolean suspend, @Parameter(hidden = true) Authentication auth) {
        return interoperabilityRecordService.suspend(interoperabilityRecordId, catalogueId, suspend, auth);
    }
}