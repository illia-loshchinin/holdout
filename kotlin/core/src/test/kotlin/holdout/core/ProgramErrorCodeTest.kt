package holdout.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgramErrorCodeTest {
    @Test
    fun `a refusal names its program error however the node, the wallet or the logs word it`() {
        assertEquals(6054, ProgramErrorCode.of("Transaction simulation failed: Error processing Instruction 0: custom program error: 0x17a6"))
        assertEquals(6047, ProgramErrorCode.of("""{"InstructionError":[0,{"Custom":6047}]}"""))
        assertEquals(6044, ProgramErrorCode.of("InstructionError(0, Custom(6044))"))
        assertNull(ProgramErrorCode.of("blockhash not found"))
        assertNull(ProgramErrorCode.of("user declined"))
        assertNull(ProgramErrorCode.of(null))
    }
}
