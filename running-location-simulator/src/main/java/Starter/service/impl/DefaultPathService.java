/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package Starter.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.maps.DirectionsApi;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.GeocodingApiRequest;
import com.google.maps.model.AddressComponent;
import com.google.maps.model.AddressComponentType;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import Starter.model.DirectionInput;
import Starter.model.Point;
import Starter.model.SupplyLocation;
import Starter.model.SimulatorFixture;
import Starter.service.PathService;
import net.sf.sprockets.Sprockets;
import net.sf.sprockets.google.Place;
import net.sf.sprockets.google.Places;
import net.sf.sprockets.google.Places.Params;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultPathService implements PathService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    public DefaultPathService() {
        super();
    }

    @Override
    public List<DirectionInput> loadDirectionInput() {
        final InputStream is = this.getClass().getResourceAsStream("/directions.json");

        try {
            return objectMapper.readValue(is, new TypeReference<List<DirectionInput>>() {
                //Just make Jackson happy
            });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public SimulatorFixture loadSimulatorFixture() {
        final InputStream is = this.getClass().getResourceAsStream("/fixture.json");

        try {
            return objectMapper.readValue(is, SimulatorFixture.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getCoordinatesFromGoogleAsPolyline(DirectionInput directionInput) {
        final GeoApiContext context = new GeoApiContext()
                .setApiKey(environment.getRequiredProperty("gpsSimmulator.googleApiKey"));
        final DirectionsApiRequest request = DirectionsApi.getDirections(
                context,
                directionInput.getFrom(),
                directionInput.getTo());

        try {
            DirectionsRoute[] routes = request.await();
            return routes[0].overviewPolyline.getEncodedPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<SupplyLocation> getSupplyLocations() {

        Sprockets.getConfig()
                .setProperty("google.api-key", environment.getRequiredProperty("gpsSimmulator.googleApiKey"));

        List<Place> stations = null;
        try {
            //White House location
            stations = Places.nearbySearch(new Params().location(38.8976763, -77.0365298).radius(5000)
                    .keyword("gasoline").openNow().maxResults(6000)).getResult();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        final List<SupplyLocation> supplyLocations = new ArrayList<>();
        final GeoApiContext context = new GeoApiContext()
                .setApiKey(environment.getRequiredProperty("gpsSimmulator.googleApiKey"));

        for (Place place : stations) {
            final SupplyLocation supplyLocation = new SupplyLocation(place.getLatitude(), place.getLongitude());
            final GeocodingApiRequest request = GeocodingApi
                    .reverseGeocode(context, new LatLng(place.getLatitude(), place.getLongitude()));

            try {
                final GeocodingResult[] result = request.await();

                String street = "";
                String streetNumber = "";

                for (AddressComponent addressComponent : result[0].addressComponents) {
                    for (AddressComponentType type : addressComponent.types) {
                        switch (type) {
                            case ROUTE:
                                street = addressComponent.shortName;
                                break;
                            case STREET_NUMBER:
                                streetNumber = addressComponent.shortName;
                                break;
                            case LOCALITY:
                                supplyLocation.setCity(addressComponent.longName);
                                break;
                            case ADMINISTRATIVE_AREA_LEVEL_1:
                                supplyLocation.setState(addressComponent.shortName);
                                break;
                            case POSTAL_CODE:
                                supplyLocation.setZip(addressComponent.shortName);
                                break;
                            default:
                                break;
                        }
                    }
                }
                supplyLocation.setAddress1(streetNumber + " " + street);
                supplyLocation.setType("Service");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }

            supplyLocations.add(supplyLocation);
        }

        return supplyLocations;
    }


    @Override
    public List<Point> getCoordinatesFromGoogle(DirectionInput directionInput) {

        final GeoApiContext context = new GeoApiContext()
                .setApiKey(environment.getRequiredProperty("gpsSimmulator.googleApiKey"));
        final DirectionsApiRequest request = DirectionsApi.getDirections(
                context,
                directionInput.getFrom(),
                directionInput.getTo());
        List<LatLng> latlongList = null;

        try {
            DirectionsRoute[] routes = request.await();

            for (DirectionsRoute route : routes) {
                latlongList = route.overviewPolyline.decodePath();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        final List<Point> points = new ArrayList<>(latlongList.size());

        for (LatLng latLng : latlongList) {
            points.add(new Point(latLng.lat, latLng.lng));
        }

        return points;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setGoogleApiKey(String googleApiKey) {
        Assert.hasText(googleApiKey, "The googleApiKey must not be empty.");

    }

}
