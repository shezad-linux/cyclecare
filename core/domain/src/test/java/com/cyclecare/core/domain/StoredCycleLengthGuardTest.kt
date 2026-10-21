package com.cyclecare.core.domain

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Spec `02` §9.4.5: "Application code reads intervals through the
 * shared extractor in `11` §7 and **never** reads `cycleLength`."
 *
 * The column is written at logging time from whatever the user last
 * reported, and `CycleRecordValidator` says so: the value "is never
 * read as a cycle length now that the domain derives it from
 * consecutive starts, and stored values can legitimately disagree with
 * the true gap." A single read of it in a screen is enough for the
 * Insights screen and the prediction screen to disagree about the same
 * user on the same day, which is the defect the shared extractor exists
 * to prevent.
 *
 * The guard has three parts, and all three have to hold for the
 * property to be enforceable:
 *
 *  1. **No application code reads the stored column.** A member access
 *     to `cycleLength` outside the persistence, migration and
 *     serialization files is an offence, and the offence names the file
 *     and the line.
 *  2. **The identifier is unambiguous.** The only types in the
 *     repository that declare a member called `cycleLength` are
 *     `CycleRecord` and the Room entity. That is what makes part 1
 *     exact: a `.cycleLength` access can only be a read of the stored
 *     column, because nothing else has a member of that name. A user
 *     interface state or an editor state that declared a member called
 *     `cycleLength` would make the guard ambiguous, so it is an offence
 *     in its own right and the field has to be named something else.
 *  3. **The guard itself is proved to work.** A guard that never fires
 *     is indistinguishable from a guard that does not work, so the
 *     scanner is run over source that is known to offend and known not
 *     to, and the verdicts are asserted.
 *
 * `UserProfile.averageCycleLength` is a different field with a
 * different name, and part 3 proves the guard leaves it alone.
 */
class StoredCycleLengthGuardTest {

    @Test
    fun noApplicationCodeReadsTheStoredCycleLength() {
        val repositoryRoot = findRepositoryRoot()
        val offenders = mutableListOf<String>()

        mainSourceRoots(repositoryRoot)
            .forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .filterNot { it.isAllowed(repositoryRoot) }
                    .forEach { file ->
                        scan(file).memberAccesses("cycleLength").forEach { access ->
                            offenders += "${file.relativeTo(repositoryRoot)}:${access.line} reads the stored cycleLength"
                        }
                    }
            }

        assertThat(offenders.joinToString("\n")).isEmpty()
    }

    @Test
    fun theStoredColumnIsTheOnlyMemberNamedCycleLength() {
        val repositoryRoot = findRepositoryRoot()
        val offenders = mutableListOf<String>()
        val declared = mutableListOf<String>()

        mainSourceRoots(repositoryRoot)
            .forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { file ->
                        scan(file).membersNamed("cycleLength").forEach { member ->
                            val where = "${file.relativeTo(repositoryRoot)}:${member.line}"
                            if (member.typeName in PERMITTED_OWNERS) {
                                declared += "$where (${member.typeName})"
                            } else {
                                offenders += "$where declares a member called cycleLength on ${member.typeName}"
                            }
                        }
                    }
            }

        // The two owners of the name are the model and the database
        // entity. Both are the column's own home.
        assertThat(declared).isNotEmpty()
        assertThat(offenders.joinToString("\n")).isEmpty()
    }

    @Test
    fun theGuardFiresOnAReadAndLeavesEverythingElseAlone() {
        val offending = scanText(
            """
            package com.cyclecare.feature.example

            import com.cyclecare.core.model.CycleRecord

            internal fun average(records: List<CycleRecord>): Int =
                records.map { it.cycleLength }.average().toInt()
            """.trimIndent(),
        )
        assertThat(offending.memberAccesses("cycleLength")).hasSize(1)
        assertThat(offending.memberAccesses("cycleLength").first().line).isEqualTo(6)

        // The profile's average is a different field, and it is not read
        // through the stored column.
        val clean = scanText(
            """
            package com.cyclecare.feature.example

            import com.cyclecare.core.model.UserProfile

            internal fun baseline(profile: UserProfile): Int = profile.averageCycleLength

            internal fun label(days: Int): String = "cycleLength=${'$'}days"
            """.trimIndent(),
        )
        assertThat(clean.memberAccesses("cycleLength")).isEmpty()

        // A local named after the column is not a read of it.
        val local = scanText(
            """
            package com.cyclecare.feature.example

            internal fun lengths(): List<Int> {
                val cycleLength = 28
                return listOf(cycleLength)
            }
            """.trimIndent(),
        )
        assertThat(local.memberAccesses("cycleLength")).isEmpty()
        assertThat(local.membersNamed("cycleLength")).isEmpty()
    }

    @Test
    fun aUserInterfaceFieldCalledCycleLengthWouldBeAnOffence() {
        // The ambiguity the second rule exists to prevent: a state class
        // with a member called `cycleLength` would make every
        // `.cycleLength` access ambiguous, so the field has to be named
        // something else. The scanner has to be able to tell a member
        // from a local to say so.
        val state = scanText(
            """
            package com.cyclecare.feature.example

            data class HomeUiState(
                val cycleLength: Int,
            )
            """.trimIndent(),
        )
        val members = state.membersNamed("cycleLength")
        assertThat(members).hasSize(1)
        assertThat(members.first().typeName).isEqualTo("HomeUiState")
        assertThat(members.first().typeName).isNotIn(PERMITTED_OWNERS)
    }

    @Test
    fun theModelRecordIsRecognisedAsTheOwnerOfTheName() {
        val model = scanText(
            """
            package com.cyclecare.core.model

            data class CycleRecord(
                val id: String,
                val cycleLength: Int,
            ) {
                val isInProgress: Boolean get() = true
            }
            """.trimIndent(),
        )
        val members = model.membersNamed("cycleLength")
        assertThat(members).hasSize(1)
        assertThat(members.first().typeName).isEqualTo("CycleRecord")
        assertThat(members.first().typeName).isIn(PERMITTED_OWNERS)
    }

    // ── the scanner ────────────────────────────────────────────────

    /** A property of a type, or a property in a primary constructor. */
    data class Member(val typeName: String, val name: String, val line: Int)

    /** A member access, and the line it is on. */
    data class Access(val name: String, val line: Int)

    class Scan internal constructor(
        private val members: List<Member>,
        private val accesses: List<Access>,
    ) {
        fun membersNamed(name: String): List<Member> = members.filter { it.name == name }

        fun memberAccesses(name: String): List<Access> = accesses.filter { it.name == name }
    }

    private fun scan(file: File): Scan = scanText(file.readText())

    /**
     * Scans Kotlin source for properties named after a field and for
     * member accesses to it.
     *
     * The scope tracking is deliberately small: it knows about type
     * bodies, function bodies and blocks, which is enough to tell a
     * property from a local. Everything it decides is asserted by the
     * tests above, so a change in its behaviour fails loudly rather
     * than silently weakening the guard.
     */
    private fun scanText(source: String): Scan {
        val declarations = mutableListOf<Member>()
        val accesses = mutableListOf<Access>()

        val scopes = ArrayDeque<Scope>()
        var pendingType: String? = null
        var pendingFunction = false
        var parenDepth = 0

        stripComments(source).lines().forEachIndexed { index, code ->
            if (code.isBlank()) return@forEachIndexed
            val line = index + 1

            val owner = scopes.lastOrNull()
            PROPERTY_DECLARATION.find(code)?.let { match ->
                val name = match.groupValues[2]
                // A property of a type, or a property in a primary
                // constructor, is a member. A property inside a
                // function body or a block is a local.
                val typeName = when {
                    owner == null -> pendingType ?: TOP_LEVEL
                    owner.kind == TYPE -> owner.typeName
                    else -> null
                }
                if (typeName != null) declarations += Member(typeName, name, line)
            }

            MEMBER_ACCESS.findAll(code).forEach { match ->
                accesses += Access(match.groupValues[1], line)
            }

            val typeHeader = TYPE_DECLARATION.find(code)
            if (typeHeader != null) {
                pendingType = typeHeader.groupValues[1]
                pendingFunction = false
            } else if (FUNCTION_DECLARATION.containsMatchIn(code)) {
                pendingFunction = true
                pendingType = null
            }

            val parenDelta = netParens(code)
            val braceDelta = netBraces(code)
            if (braceDelta > 0) {
                // The first brace on the line opens the scope the
                // pending header was waiting for.
                if (pendingType != null) {
                    scopes.addLast(Scope(TYPE, pendingType))
                    pendingType = null
                } else if (pendingFunction) {
                    scopes.addLast(Scope(FUNCTION, null))
                    pendingFunction = false
                } else {
                    scopes.addLast(Scope(BLOCK, null))
                }
                repeat(braceDelta - 1) { scopes.addLast(Scope(BLOCK, null)) }
            }

            parenDepth += parenDelta
            if (parenDepth < 0) parenDepth = 0

            if (pendingFunction && parenDepth == 0 && braceDelta == 0) {
                // An expression-bodied function never opens a brace, so
                // the header is finished with.
                pendingFunction = false
            }
            if (pendingType != null && parenDepth == 0 && braceDelta == 0) {
                // A class with no body: the parameter list closed and
                // nothing opened, so no type scope is coming.
                pendingType = null
            }

            repeat(if (braceDelta < 0) -braceDelta else 0) {
                if (scopes.isNotEmpty()) scopes.removeLast()
            }
        }

        return Scan(declarations, accesses)
    }

    /** A scope in the source: a type body, a function body or a block. */
    private data class Scope(val kind: String, val typeName: String?)

    /**
     * Removes comments and string literals.
     *
     * A word inside a comment is not code, and a word inside a string
     * is not an identifier, so both are removed before anything is
     * matched against them.
     */
    private fun stripComments(source: String): String {
        val result = StringBuilder()
        var index = 0
        var inBlockComment = false
        var inString = false
        var inChar = false
        while (index < source.length) {
            val character = source[index]
            val next = source.getOrNull(index + 1)
            when {
                inBlockComment -> {
                    if (character == '*' && next == '/') {
                        inBlockComment = false
                        index++
                    }
                }
                inString -> {
                    if (character == '\\') {
                        index++
                    } else if (character == '"') {
                        inString = false
                    }
                }
                inChar -> {
                    if (character == '\\') {
                        index++
                    } else if (character == '\'') {
                        inChar = false
                    }
                }
                character == '/' && next == '*' -> {
                    inBlockComment = true
                    index++
                }
                character == '/' && next == '/' -> {
                    // A line comment runs to the end of the line.
                    while (index < source.length && source[index] != '\n') index++
                    result.append('\n')
                    continue
                }
                character == '"' -> inString = true
                character == '\'' -> inChar = true
                else -> result.append(character)
            }
            index++
        }
        return result.toString()
    }

    private fun netBraces(code: String): Int {
        var count = 0
        code.forEach { character ->
            when (character) {
                '{' -> count++
                '}' -> count--
            }
        }
        return count
    }

    private fun netParens(code: String): Int {
        var count = 0
        code.forEach { character ->
            when (character) {
                '(' -> count++
                ')' -> count--
            }
        }
        return count
    }

    private fun findRepositoryRoot(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            if (File(candidate, "settings.gradle.kts").exists()) return candidate
            candidate = candidate.parentFile
        }
        throw AssertionError("Could not find the repository root from ${File("").path}")
    }

    /**
     * Every `src/main` directory in the repository.
     *
     * The module layout is discovered rather than listed, so a new
     * module is covered by the guard without this file being edited.
     */
    private fun mainSourceRoots(repositoryRoot: File): List<File> =
        repositoryRoot.walkTopDown()
            .filter { it.isDirectory && it.name == "main" && it.parentFile?.name == "src" }
            .filterNot { it.path.contains(File.separator + "build" + File.separator) }
            .toList()

    /**
     * Whether this file is one of the places the column legitimately
     * lives.
     *
     * The allowlist is by path, and a directory entry covers everything
     * under it, so the migration module is covered as a whole while
     * the two data-layer files are named individually.
     */
    private fun File.isAllowed(repositoryRoot: File): Boolean =
        ALLOWED_PATHS.any { relative ->
            val candidate = File(repositoryRoot, relative)
            this.path == candidate.path || this.path.startsWith(candidate.path + File.separator)
        }

    private companion object {
        /**
         * Where the stored column is legitimately handled.
         *
         * The migration reconciles the column and verifies it. The
         * mapper carries it between the entity and the model. The
         * serializer writes it into a backup and reads it back. All
         * three are the column's own home.
         */
        val ALLOWED_PATHS = listOf(
            "core/database/src/main",
            "core/data/src/main/java/com/cyclecare/core/data/EntityMappers.kt",
            "core/data/src/main/java/com/cyclecare/core/data/BackupPayloadSerializer.kt",
        )

        /**
         * The types allowed to declare a member called `cycleLength`.
         *
         * The model record and the database entity. Nothing else may
         * have a member of that name, which is what makes the member
         * access rule exact.
         */
        val PERMITTED_OWNERS = setOf("CycleRecord", "CycleRecordEntity")

        private const val TYPE = "type"
        private const val FUNCTION = "function"
        private const val BLOCK = "block"
        private const val TOP_LEVEL = "<top level>"

        /** `val name` or `var name`, with any modifiers. */
        private val PROPERTY_DECLARATION =
            Regex("""^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:private|internal|public|protected|override|open|abstract|lateinit|const|final|external|suspend|inline|operator|infix|tailrec)\s+)*(val|var)\s+(\w+)""")

        /** A class, object or interface header. */
        private val TYPE_DECLARATION = Regex("""\b(?:class|object|interface)\s+(\w+)""")

        /** A function header. */
        private val FUNCTION_DECLARATION = Regex("""\bfun\b""")

        /** A member access to a field. */
        private val MEMBER_ACCESS = Regex("""\.\s*(\w+)""")
    }
}
