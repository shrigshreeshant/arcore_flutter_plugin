import android.media.Image
import android.opengl.GLES30
import android.util.Log
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class YuvToRgbRenderer {



    private val TAG = "YUV2RGB"

    private var framebuffer = IntArray(1)
    private var texture = IntArray(1)
    private var program = 0
    private var outputWidth = 0
    private var outputHeight = 0

    private var yuvTextures = IntArray(3)
    private var quadVAO = IntArray(1)

    fun initShader() {
        Log.d(TAG, "🧱 Starting shader compilation")

        val vertexShaderCode = """
        #version 300 es
        in vec4 aPosition;
        in vec2 aTexCoord;
        out vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
        """.trimIndent()

        val fragmentShaderCode = """
        #version 300 es
        precision mediump float;
        in vec2 vTexCoord;
        layout(location = 0) out vec4 outColor;
        uniform sampler2D yTexture;
        uniform sampler2D uTexture;
        uniform sampler2D vTexture;

        void main() {
            float y = texture(yTexture, vTexCoord).r;
            float u = texture(uTexture, vTexCoord).r - 0.5;
            float v = texture(vTexture, vTexCoord).r - 0.5;

            float r = y + 1.402 * v;
            float g = y - 0.344 * u - 0.714 * v;
            float b = y + 1.772 * u;

            outColor = vec4(r, g, b, 1.0);
        }
        """.trimIndent()

        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "❌ Shader link failed:\n${GLES30.glGetProgramInfoLog(program)}")
            GLES30.glDeleteProgram(program)
            program = 0
        } else {
            Log.d(TAG, "✅ Shader linked successfully")
        }
    }

    private fun compileShader(type: Int, code: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, code)
        GLES30.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "❌ Shader compile error: $error")
            GLES30.glDeleteShader(shader)
            return 0
        } else {
            Log.d(TAG, "✅ Shader compiled: ${if (type == GLES30.GL_VERTEX_SHADER) "VERTEX" else "FRAGMENT"}")
            return shader
        }
    }

    fun initFramebuffer(width: Int, height: Int) {
        Log.d(TAG, "🔧 Initializing framebuffer with size: $width x $height")
        outputWidth = width
        outputHeight = height

        GLES30.glGenFramebuffers(1, framebuffer, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer[0])

        GLES30.glGenTextures(1, texture, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, texture[0], 0
        )

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "❌ Framebuffer is not complete: $status")
        } else {
            Log.d(TAG, "✅ Framebuffer created successfully")
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }
    private var pboIds = IntArray(2)
    private var pboIndex = 0
    private var pboInitialized = false


    fun renderToBuffer(image: Image, width: Int, height: Int): ByteBuffer? {
        if (program == 0) {
            Log.e(TAG, "❌ Shader program not initialized! Call initShader() first.")
            return null
        }
        Log.d(TAG, "🎬 Starting YUV to RGB render")

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer[0])
        GLES30.glViewport(0, 0, outputWidth, outputHeight)

        uploadYUVTextures(image)
        Log.d(TAG, "🎞️ YUV textures uploaded")

        GLES30.glUseProgram(program)
        drawFullScreenQuad()
        Log.d(TAG, "🖼️ Fullscreen quad drawn")

        val buffer = ByteBuffer.allocateDirect(outputWidth * outputHeight * 4)
        buffer.order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(
            0, 0, outputWidth, outputHeight,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer
        )
        Log.d(TAG, "📤 RGB data read into buffer")

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        buffer.position(0)
        return buffer
    }

    private fun uploadYUVTextures(image: Image) {
        if (yuvTextures.all { it == 0 }) {
            GLES30.glGenTextures(3, yuvTextures, 0)
            Log.d(TAG, "🆕 Generated YUV textures: ${yuvTextures.toList()}")
        }

        val planes = image.planes

        for (i in 0..2) {
            val plane = planes[i]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            val width = if (i == 0) image.width else image.width / 2
            val height = if (i == 0) image.height else image.height / 2

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + i)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, yuvTextures[i])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)

            val data = ByteArray(width * height)
            var offset = 0

            try {
                for (row in 0 until height) {
                    val rowStart = row * rowStride
                    buffer.position(rowStart)
                    var col = 0
                    while (col < width && buffer.position() < buffer.limit()) {
                        data[offset++] = buffer.get()
                        col++
                        if (pixelStride > 1) {
                            val nextPos = buffer.position() + (pixelStride - 1)
                            buffer.position(nextPos.coerceAtMost(buffer.limit() - 1))
                        }
                    }
                }
            } catch (e: BufferUnderflowException) {
                Log.e(TAG, "❌ Buffer underflow at plane $i: ${e.message}")
                return
            }

            val texBuffer = ByteBuffer.wrap(data)

            // Use GL_RED instead of deprecated GL_LUMINANCE in GLES3.0+
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_R8,
                width,
                height,
                0,
                GLES30.GL_RED,
                GLES30.GL_UNSIGNED_BYTE,
                texBuffer
            )

            val error = GLES30.glGetError()
            if (error != GLES30.GL_NO_ERROR) {
                Log.e(TAG, "❌ glTexImage2D error on plane $i: $error")
            } else {
                Log.d(TAG, "✅ Uploaded plane $i (size: ${width}x$height, pixelStride: $pixelStride)")
            }
        }
    }

    private fun drawFullScreenQuad() {
        if (quadVAO[0] == 0) {
            Log.d(TAG, "🔧 Initializing fullscreen quad VAO")

            val quadVertices = floatArrayOf(
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                -1f, 1f, 0f, 1f,
                1f, 1f, 1f, 1f,
            )
            val vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            vertexBuffer.put(quadVertices).position(0)

            val vao = IntArray(1)
            val vbo = IntArray(1)
            GLES30.glGenVertexArrays(1, vao, 0)
            GLES30.glGenBuffers(1, vbo, 0)

            GLES30.glBindVertexArray(vao[0])
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                quadVertices.size * 4,
                vertexBuffer,
                GLES30.GL_STATIC_DRAW
            )

            val posLoc = GLES30.glGetAttribLocation(program, "aPosition")
            val texLoc = GLES30.glGetAttribLocation(program, "aTexCoord")

            GLES30.glEnableVertexAttribArray(posLoc)
            GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 4 * 4, 0)

            GLES30.glEnableVertexAttribArray(texLoc)
            GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 4 * 4, 2 * 4)

            GLES30.glBindVertexArray(0)

            quadVAO[0] = vao[0]
            Log.d(TAG, "✅ Fullscreen quad VAO ready")
        }

        // Bind YUV textures to the correct texture units & uniforms
        for (i in 0..2) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + i)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, yuvTextures[i])
            val loc = GLES30.glGetUniformLocation(program, when (i) {
                0 -> "yTexture"
                1 -> "uTexture"
                2 -> "vTexture"
                else -> ""
            })
            GLES30.glUniform1i(loc, i)
            Log.d(TAG, "Bound texture unit $i to uniform ${if(i==0)"yTexture" else if(i==1)"uTexture" else "vTexture"} at location $loc")
        }

        GLES30.glBindVertexArray(quadVAO[0])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)

        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "❌ Draw call error: $error")
        }
    }
}


//class YuvToRgbRenderer {
//
//    private val TAG = "YUV2RGB"
//
//    private var framebuffer = IntArray(1)
//    private var texture = IntArray(1)
//    private var program = 0
//    private var outputWidth = 0
//    private var outputHeight = 0
//
//    fun initFramebuffer(width: Int, height: Int) {
//        Log.d(TAG, "🔧 Initializing framebuffer with size: $width x $height")
//        outputWidth = width
//        outputHeight = height
//
//        // Create framebuffer
//        GLES30.glGenFramebuffers(1, framebuffer, 0)
//        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer[0])
//
//        // Create texture to render to
//        GLES30.glGenTextures(1, texture, 0)
//        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])
//        GLES30.glTexImage2D(
//            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
//            width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
//        )
//        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
//        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
//        GLES30.glFramebufferTexture2D(
//            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
//            GLES30.GL_TEXTURE_2D, texture[0], 0
//        )
//
//        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
//        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
//            Log.e(TAG, "❌ Framebuffer is not complete: $status")
//        } else {
//            Log.d(TAG, "✅ Framebuffer created successfully")
//        }
//
//        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
//    }
//
//    fun renderToBuffer(image: Image, width: Int, height: Int): ByteBuffer {
//        Log.d(TAG, "🎬 Starting YUV to RGB render")
//
//        // Bind framebuffer
//        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer[0])
//        GLES30.glViewport(0, 0, outputWidth, outputHeight)
//
//        // Upload YUV textures
//        uploadYUVTextures(image)
//        Log.d(TAG, "🎞️ YUV textures uploaded")
//
//        // Use shader
//        GLES30.glUseProgram(program)
//        drawFullScreenQuad()
//        Log.d(TAG, "🖼️ Fullscreen quad drawn")
//
//        // Read pixels into ByteBuffer
//        val buffer = ByteBuffer.allocateDirect(outputWidth * outputHeight * 4)
//        buffer.order(ByteOrder.nativeOrder())
//        GLES30.glReadPixels(
//            0, 0, outputWidth, outputHeight,
//            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer
//        )
//        Log.d(TAG, "📤 RGB data read into buffer")
//
//        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
//        return buffer
//    }
//
//    private var yuvTextures = IntArray(3)
//
//    private fun uploadYUVTextures(image: Image) {
//        if (yuvTextures[0] == 0) {
//            GLES30.glGenTextures(3, yuvTextures, 0)
//            Log.d(TAG, "🆕 Generated YUV textures: ${yuvTextures.toList()}")
//        }
//
//        val planes = image.planes
//
//        for (i in 0..2) {
//            val plane = planes[i]
//            val buffer = plane.buffer
//            val rowStride = plane.rowStride
//            val pixelStride = plane.pixelStride
//
//            val width = if (i == 0) image.width else image.width / 2
//            val height = if (i == 0) image.height else image.height / 2
//
//            val texId = yuvTextures[i]
//            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
//            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
//            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
//            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
//
//            // Read only the valid pixel data
//            val data = ByteArray(width * height)
//            var offset = 0
//            try {
//                for (row in 0 until height) {
//                    val rowStart = row * rowStride
//                    // Clamp rowStart so it's never beyond buffer.limit() - 1
//                    val safeRowStart = rowStart.coerceAtMost(buffer.limit() - 1)
//                    buffer.position(safeRowStart)
//
//                    var col = 0
//                    while (col < width && buffer.position() < buffer.limit()) {
//                        data[offset++] = buffer.get()
//                        col += 1
//                        if (pixelStride > 1) {
//                            val nextPos = buffer.position() + (pixelStride - 1)
//                            // Clamp nextPos to buffer.limit() - 1 max
//                            val safeNextPos = nextPos.coerceAtMost(buffer.limit() - 1)
//                            buffer.position(safeNextPos)
//                        }
//                    }
//                }
//
//            } catch (e: BufferUnderflowException) {
//                Log.e(TAG, "❌ Buffer underflow at plane $i: ${e.message}")
//                return
//            }
//
//            val texBuffer = ByteBuffer.wrap(data)
//            GLES30.glTexImage2D(
//                GLES30.GL_TEXTURE_2D,
//                0,
//                GLES30.GL_LUMINANCE,
//                width,
//                height,
//                0,
//                GLES30.GL_LUMINANCE,
//                GLES30.GL_UNSIGNED_BYTE,
//                texBuffer
//            )
//
//            val error = GLES30.glGetError()
//            if (error != GLES30.GL_NO_ERROR) {
//                Log.e(TAG, "❌ glTexImage2D error on plane $i: $error")
//            } else {
//                Log.d(TAG, "✅ Uploaded plane $i (size: ${width}x$height, pixelStride: $pixelStride)")
//            }
//        }
//    }
//
//    private var quadVAO = IntArray(1)
//
//    private fun drawFullScreenQuad() {
//        if (quadVAO[0] == 0) {
//            Log.d(TAG, "🔧 Initializing fullscreen quad VAO")
//
//            val quadVertices = floatArrayOf(
//                -1f, -1f, 0f, 0f,
//                1f, -1f, 1f, 0f,
//                -1f, 1f, 0f, 1f,
//                1f, 1f, 1f, 1f,
//            )
//            val vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
//                .order(ByteOrder.nativeOrder())
//                .asFloatBuffer()
//            vertexBuffer.put(quadVertices).position(0)
//
//            val vao = IntArray(1)
//            val vbo = IntArray(1)
//            GLES30.glGenVertexArrays(1, vao, 0)
//            GLES30.glGenBuffers(1, vbo, 0)
//
//            GLES30.glBindVertexArray(vao[0])
//            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
//            GLES30.glBufferData(
//                GLES30.GL_ARRAY_BUFFER,
//                quadVertices.size * 4,
//                vertexBuffer,
//                GLES30.GL_STATIC_DRAW
//            )
//
//            val posLoc = GLES30.glGetAttribLocation(program, "aPosition")
//            val texLoc = GLES30.glGetAttribLocation(program, "aTexCoord")
//
//            GLES30.glEnableVertexAttribArray(posLoc)
//            GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 4 * 4, 0)
//
//            GLES30.glEnableVertexAttribArray(texLoc)
//            GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 4 * 4, 2 * 4)
//
//            GLES30.glBindVertexArray(0)
//
//            quadVAO[0] = vao[0]
//            Log.d(TAG, "✅ Fullscreen quad VAO ready")
//        }
//
//        // Bind textures
//        for (i in 0..2) {
//            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + i)
//            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, yuvTextures[i])
//            GLES30.glUniform1i(
//                GLES30.glGetUniformLocation(
//                    program, when (i) {
//                        0 -> "yTexture"
//                        1 -> "uTexture"
//                        2 -> "vTexture"
//                        else -> ""
//                    }
//                ), i
//            )
//        }
//
//        // Draw
//        GLES30.glBindVertexArray(quadVAO[0])
//        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
//        GLES30.glBindVertexArray(0)
//
//        val error = GLES30.glGetError()
//        if (error != GLES30.GL_NO_ERROR) {
//            Log.e(TAG, "❌ Draw call error: $error")
//
//
//            fun initShader() {
//                Log.d(TAG, "🧱 Starting shader compilation")
//
//                val vertexShaderCode = """
//        #version 300 es
//        in vec4 aPosition;
//        in vec2 aTexCoord;
//        out vec2 vTexCoord;
//        void main() {
//            gl_Position = aPosition;
//            vTexCoord = aTexCoord;
//        }
//    """.trimIndent()
//
//                val fragmentShaderCode = """
//        #version 300 es
//        precision mediump float;
//        in vec2 vTexCoord;
//        layout(location = 0) out vec4 outColor;
//        uniform sampler2D yTexture;
//        uniform sampler2D uTexture;
//        uniform sampler2D vTexture;
//
//        void main() {
//            float y = texture(yTexture, vTexCoord).r;
//            float u = texture(uTexture, vTexCoord).r - 0.5;
//            float v = texture(vTexture, vTexCoord).r - 0.5;
//
//            float r = y + 1.402 * v;
//            float g = y - 0.344 * u - 0.714 * v;
//            float b = y + 1.772 * u;
//
//            outColor = vec4(r, g, b, 1.0);
//        }
//    """.trimIndent()
//
//                val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexShaderCode)
//                val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderCode)
//
//                program = GLES30.glCreateProgram().also {
//                    GLES30.glAttachShader(it, vertexShader)
//                    GLES30.glAttachShader(it, fragmentShader)
//                    GLES30.glLinkProgram(it)
//
//                    val linkStatus = IntArray(1)
//                    GLES30.glGetProgramiv(it, GLES30.GL_LINK_STATUS, linkStatus, 0)
//                    if (linkStatus[0] == 0) {
//                        Log.e(TAG, "❌ Shader link failed:\n${GLES30.glGetProgramInfoLog(it)}")
//                        GLES30.glDeleteProgram(it)
//                    } else {
//                        Log.d(TAG, "✅ Shader linked successfully")
//                    }
//                }
//            }
//
//        }
//    }
//
//    private fun compileShader(type: Int, code: String): Int {
//        val shader = GLES30.glCreateShader(type)
//        GLES30.glShaderSource(shader, code)
//        GLES30.glCompileShader(shader)
//
//        val compiled = IntArray(1)
//        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
//        if (compiled[0] == 0) {
//            val error = GLES30.glGetShaderInfoLog(shader)
//            Log.e(TAG, "❌ Shader compile error: $error")
//            GLES30.glDeleteShader(shader)
//        } else {
//            Log.d(TAG, "✅ Shader compiled: ${if (type == GLES30.GL_VERTEX_SHADER) "VERTEX" else "FRAGMENT"}")
//        }
//
//        return shader
//    }

//}