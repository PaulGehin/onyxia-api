package fr.insee.onyxia.api.controller.pub;

import fr.insee.onyxia.api.configuration.CatalogWrapper;
import fr.insee.onyxia.api.configuration.Catalogs;
import fr.insee.onyxia.api.configuration.NotFoundException;
import fr.insee.onyxia.api.services.CatalogService;
import fr.insee.onyxia.model.catalog.Config.Property;
import fr.insee.onyxia.model.catalog.Config.Property.XForm;
import fr.insee.onyxia.model.catalog.Config.Property.XOnyxia;
import fr.insee.onyxia.model.catalog.Pkg;
import fr.insee.onyxia.model.helm.Chart;
import fr.insee.onyxia.model.region.Region;
import fr.insee.onyxia.model.service.Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public")
@RequestMapping(value = {"/public/catalog", "/public/catalogs"})
@RestController
public class CatalogController {

    @Autowired private CatalogService catalogService;

    @Operation(
            summary = "List available catalogs and packages for installing.",
            description =
                    "List available catalogs and packages for installing in the first Region configuration of this Onyxia API.")
    @GetMapping
    public Catalogs getCatalogs(@Parameter(hidden = true) Region region) {
        Catalogs filteredCatalogs = new Catalogs();
        filteredCatalogs.setCatalogs(
                catalogService.getCatalogs().getCatalogs().stream()
                        .filter((catalog) -> isCatalogEnabled(region, catalog))
                        .collect(Collectors.toList()));
        return filteredCatalogs;
    }

    @Operation(
            summary = "List available packages for installing given a catalog.",
            description =
                    "List available packages for installing given a catalog with detailed information on the packages including: descriptions, sources, and configuration options.",
            parameters = {
                @Parameter(
                        required = true,
                        name = "catalogId",
                        description = "Unique ID of the enabled catalog for this Onyxia API.",
                        in = ParameterIn.PATH)
            })
    @GetMapping("{catalogId}")
    public CatalogWrapper getCatalogById(@PathVariable String catalogId) {
        CatalogWrapper wrapper = catalogService.getCatalogById(catalogId);
        if (wrapper == null) {
            throw new NotFoundException();
        }
        return wrapper;
    }

    @Operation(
            summary = "Get a service package information from a specific catalog.",
            description =
                    "Get a service package information from a specific catalog, with detailed information on the package including: descriptions, sources, and configuration options.",
            parameters = {
                @Parameter(
                        required = true,
                        name = "catalogId",
                        description = "Unique ID of the enabled catalog for this Onyxia API.",
                        in = ParameterIn.PATH),
                @Parameter(
                        required = true,
                        name = "packageName",
                        description = "Unique name of the package from the selected catalog.",
                        in = ParameterIn.PATH)
            })
    @GetMapping("{catalogId}/{packageName}")
    public Pkg getPackage(@PathVariable String catalogId, @PathVariable String packageName) {
        Pkg pkg = catalogService.getPackage(catalogId, packageName);
        addCustomOnyxiaProperties(pkg);
        if (pkg == null) {
            throw new NotFoundException();
        }
        return pkg;
    }

    @Operation(
            summary = "Get a helm chart from a specific catalog by version.",
            description =
                    "Get a helm chart from a specific catalog by version, with detailed information on the package including: descriptions, sources, and configuration options.",
            parameters = {
                @Parameter(
                        required = true,
                        name = "catalogId",
                        description = "Unique ID of the enabled catalog for this Onyxia API.",
                        in = ParameterIn.PATH),
                @Parameter(
                        required = true,
                        name = "chartName",
                        description = "Unique name of the chart from the selected catalog.",
                        in = ParameterIn.PATH),
                @Parameter(
                        required = true,
                        name = "version",
                        description = "Version of the chart",
                        in = ParameterIn.PATH)
            })
    @GetMapping("{catalogId}/charts/{chartName}/versions/{version}")
    public Chart getChartByVersion(
            @PathVariable String catalogId,
            @PathVariable String chartName,
            @PathVariable String version) {
        Chart chart =
                catalogService
                        .getChartByVersion(catalogId, chartName, version)
                        .orElseThrow(NotFoundException::new);
        addCustomOnyxiaProperties(chart);
        return chart;
    }

    @Operation(
            summary = "Get all versions of a chart from a specific catalog.",
            description =
                    "Get all versions of a chart from a specific catalog, with detailed information on the package including: descriptions, sources, and configuration options.",
            parameters = {
                @Parameter(
                        required = true,
                        name = "catalogId",
                        description = "Unique ID of the enabled catalog for this Onyxia API.",
                        in = ParameterIn.PATH),
                @Parameter(
                        required = true,
                        name = "chartName",
                        description = "Unique name of the chart from the selected catalog.",
                        in = ParameterIn.PATH)
            })
    @GetMapping("{catalogId}/charts/{chartName}")
    public List<Chart> getCharts(@PathVariable String catalogId, @PathVariable String chartName) {
        List<Chart> charts =
                catalogService.getCharts(catalogId, chartName).orElseThrow(NotFoundException::new);
        charts.stream().forEach(this::addCustomOnyxiaProperties);
        return charts;
    }

    private boolean isCatalogEnabled(Region region, CatalogWrapper catalog) {
        if (region == null) {
            return true;
        }

        if (catalog.getType().equalsIgnoreCase("HELM")) {
            return region.getServices().getType().equals(Service.ServiceType.KUBERNETES);
        }

        return false;
    }

    private void addCustomOnyxiaProperties(Pkg pkg) {
        Map<String, Property> properties = pkg.getConfig().getProperties().getProperties();
        Property property = new Property();
        property.setDescription("Onyxia specific configuration");
        property.setType("object");
        property.setProperties(new HashMap<>());
        Map<String, Property> onyxiaProperties = new HashMap<>();
        Property customName = new Property();
        customName.setType("string");
        customName.setDescription("Service custom name");
        customName.setDefaut(pkg.getName());
        customName.setTitle("Custom name");
        onyxiaProperties.put("friendlyName", customName);
        Property userDefinedValues = new Property();
        userDefinedValues.setType("string");
        userDefinedValues.setDescription("Values defined by the end user");
        userDefinedValues.setDefaut("");
        userDefinedValues.setTitle("User defined values");
        XOnyxia xonyxiaUserDefinedValues = new XOnyxia();
        xonyxiaUserDefinedValues.setHidden(true);
        userDefinedValues.setXonyxia(xonyxiaUserDefinedValues);
        onyxiaProperties.put("userDefinedValues", userDefinedValues);
        Property owner = new Property();
        owner.setType("string");
        owner.setDescription("Owner of the chart");
        owner.setDefaut("owner");
        owner.setTitle("Owner");
        XForm xform = new XForm();
        xform.setValue("{{user.idep}}");
        xform.setHidden(true);
        owner.setXform(xform);
        XOnyxia xonyxia = new XOnyxia();
        xonyxia.setOverwriteDefaultWith("{{user.idep}}");
        xonyxia.setHidden(true);
        owner.setXonyxia(xonyxia);
        onyxiaProperties.put("owner", owner);

        Property share = new Property();
        share.setType("boolean");
        share.setDescription("Enable share for this service");
        share.setDefaut(false);
        share.setTitle("Share");
        onyxiaProperties.put("share", share);

        property.setProperties(onyxiaProperties);

        properties.put("onyxia", property);
    }
}
