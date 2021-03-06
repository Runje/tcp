package blue.koenig.tcp

import java.nio.ByteBuffer

/**
 * Copyright Hensoldt Sensors GmbH
 *
 *
 * Version: $Revision: 1 $
 * Author: Thomas König
 * Creation date: 22.01.2018
 * Last author: $Author: Thomas König $
 * Last changed date: $Date: 22.01.2018 $
 */

interface Message {

    val byteBuffer: ByteBuffer
}
