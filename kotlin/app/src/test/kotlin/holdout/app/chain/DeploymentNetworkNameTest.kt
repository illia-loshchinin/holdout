package holdout.app.chain

import holdout.core.Rpc
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a member is told failed to answer (spec 005): devnet for any endpoint
 * reached by name, a provider's included, and the local validator only for one
 * reached by address (PR #67: a build pointed at a provider's devnet endpoint
 * said "The local validator can't be reached").
 */
class DeploymentNetworkNameTest {
    @Test
    fun `the public devnet endpoint and a provider's are devnet`() {
        assertEquals("Devnet", Deployment.networkNameOf(Rpc.DEVNET))
        assertEquals("Devnet", Deployment.networkNameOf("https://devnet.helius-rpc.com/?api-key=x"))
        assertEquals("Devnet", Deployment.networkNameOf("https://rpc.example.org:8443/path"))
    }

    @Test
    fun `an endpoint on this machine or the local network is the local validator`() {
        assertEquals("The local validator", Deployment.networkNameOf("http://127.0.0.1:8899"))
        assertEquals("The local validator", Deployment.networkNameOf("http://10.0.2.2:8899"))
        assertEquals("The local validator", Deployment.networkNameOf("http://192.168.1.20:8899/"))
        assertEquals("The local validator", Deployment.networkNameOf("http://localhost:8899"))
        assertEquals("The local validator", Deployment.networkNameOf("http://[::1]:8899"))
    }
}
