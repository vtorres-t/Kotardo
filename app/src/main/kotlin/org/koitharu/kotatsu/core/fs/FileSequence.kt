package org.koitharu.kotatsu.core.fs

import org.koitharu.kotatsu.core.util.CloseableSequence
import org.koitharu.kotatsu.core.util.iterator.MappingIterator
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

sealed interface FileSequence : CloseableSequence<File> {
	class StreamImpl(dir: File) : FileSequence {

		private val stream = Files.newDirectoryStream(dir.toPath())

		override fun iterator(): Iterator<File> = MappingIterator(stream.iterator(), Path::toFile)

		override fun close() = stream.close()
	}
}
