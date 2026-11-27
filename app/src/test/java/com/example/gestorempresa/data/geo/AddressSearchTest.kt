package com.example.gestorempresa.data.geo

import org.junit.Assert.*
import org.junit.Test

class AddressSearchTest {
    // Respuesta real de Photon para «Gran Via Mur» (recortada), con un resultado repetido.
    private val photon = """
        {"features":[
         {"geometry":{"coordinates":[-1.1304397,37.9911617],"type":"Point"},"type":"Feature",
          "properties":{"osm_type":"W","name":"Gran Vía Alfonso X El Sabio","type":"street","countrycode":"ES","postcode":"30008",
           "city":"Murcia","county":"Área Metropolitana de Murcia","state":"Región de Murcia","country":"España"}},
         {"geometry":{"coordinates":[-1.1301869,37.990415],"type":"Point"},"type":"Feature",
          "properties":{"osm_type":"W","name":"Gran Vía Alfonso X El Sabio","type":"street","countrycode":"ES","postcode":"30008",
           "city":"Murcia","county":"Área Metropolitana de Murcia","state":"Región de Murcia","country":"España"}},
         {"geometry":{"coordinates":[-1.128,37.9838],"type":"Point"},"type":"Feature",
          "properties":{"name":"Cafélab","type":"house","street":"Calle de los Apóstoles","housenumber":"14","countrycode":"ES",
           "postcode":"30001","city":"Murcia","state":"Región de Murcia","country":"España"}}
        ]}""".trimIndent()

    @Test fun parsesStreetsAndHousesWithAllParts() {
        val results = parsePhotonResponse(photon)
        assertEquals(2, results.size) // la calle repetida aparece una sola vez
        val street = results[0]
        assertEquals("Gran Vía Alfonso X El Sabio", street.line1)
        assertEquals("Murcia", street.locality)
        assertEquals("Murcia", street.province)
        assertEquals("30008", street.postcode)
        assertEquals("España", street.country)
        assertEquals(37.9911617, street.latitude, 1e-9)
        assertEquals("Gran Vía Alfonso X El Sabio, 30008 Murcia, España", street.fullAddress)
        val house = results[1]
        assertEquals("Calle de los Apóstoles 14", house.line1)
        assertEquals("Cafélab", house.placeName)
    }

    @Test fun ignoresBrokenResponses() {
        assertTrue(parsePhotonResponse("""{"features":[]}""").isEmpty())
        assertTrue(parsePhotonResponse("""{"other":1}""").isEmpty())
    }

    @Test fun derivesSpanishProvinceFromPostcode() {
        assertEquals("Murcia", spanishProvinceFromPostcode("30008"))
        assertEquals("Madrid", spanishProvinceFromPostcode("28013"))
        assertEquals("Álava", spanishProvinceFromPostcode("01001"))
        assertEquals("Melilla", spanishProvinceFromPostcode("52001"))
        assertNull(spanishProvinceFromPostcode("99000"))
        assertNull(spanishProvinceFromPostcode(""))
    }

    @Test fun detectsWhenAddressAndMarkerDisagree() {
        val granVia = 37.9911617 to -1.1304397
        assertEquals(LocationConsistency.NoMarker, checkLocationConsistency(granVia, null))
        assertEquals(LocationConsistency.Unverified, checkLocationConsistency(null, granVia))
        assertEquals(LocationConsistency.Consistent, checkLocationConsistency(granVia, 37.9915 to -1.1305))
        val cartagena = 37.5995 to -0.9830
        val mismatch = checkLocationConsistency(granVia, cartagena)
        assertTrue(mismatch is LocationConsistency.Mismatch && mismatch.meters in 40_000.0..50_000.0)
    }
}
