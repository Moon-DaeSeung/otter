package schema

import com.goodgoodman.otter.schema.ColumnSchema
import com.goodgoodman.otter.schema.Constraint
import com.goodgoodman.otter.schema.ReferenceSchema
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColumnSchemaTests {
    @Test
    fun `Constraint 지정`() {
        val columnSchema = ColumnSchema().apply {
            setConstraint(Constraint.NOT_NULL, Constraint.PRIMARY)
        }
        assertTrue(columnSchema.constraints.containsAll(listOf(Constraint.NOT_NULL, Constraint.PRIMARY)))
    }

    @Test
    fun `Constraint NONE 입력시 무시처리`() {
        val columnSchema = ColumnSchema().apply {
            setConstraint(Constraint.NONE)
        }
        assertEquals(0, columnSchema.constraints.size)
    }

    @Test
    fun `Constraint 지정 중복호출시 마지막만 적용`() {
        val columnSchema = ColumnSchema().apply {
            setConstraint(Constraint.NOT_NULL, Constraint.PRIMARY)
            setConstraint(Constraint.PRIMARY)
        }
        assertEquals(listOf(Constraint.PRIMARY), columnSchema.constraints.toList())
    }

    @Test
    fun `외래키 지정`() {
        val columnName = "temp_id"
        val referenceSchema = ReferenceSchema().apply {
            fromColumn = columnName
            toTable = "temp"
            toColumn = "id"
            key = "fk_temp_id"
        }
        val columnSchema = ColumnSchema().apply {
            name = columnName
            reference {
                toTable = referenceSchema.toTable
                toColumn = referenceSchema.toColumn
                key = referenceSchema.key
            }
        }
        assertEquals(referenceSchema.toString(), columnSchema.referenceSchema.first().toString())
    }

    @Test
    fun `외래키 복수 지정`() {
        val referenceSchema = ReferenceSchema().apply {
            toTable = "temp"
            toColumn = "id"
            key = "fk_temp_id"
        }
        val columnSchema = ColumnSchema().apply {
            name = "temp_id"
            reference {
                toTable = referenceSchema.toTable
                toColumn = referenceSchema.toColumn
                key = referenceSchema.key
            }
            reference {
                toTable = referenceSchema.toTable
                toColumn = referenceSchema.toColumn
                key = referenceSchema.key
            }
        }
        assertEquals(2, columnSchema.referenceSchema.size)
    }
}