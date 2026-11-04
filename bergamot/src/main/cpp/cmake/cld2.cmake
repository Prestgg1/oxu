# SPDX-License-Identifier: GPL-3.0-or-later
# Builds the parts of CLD2 needed for plain-text language detection.
if(TARGET cld2)
    return()
endif()

set(CLD2_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/cld2)
set(CLD2_SOURCES
    internal/cldutil.cc
    internal/cldutil_shared.cc
    internal/compact_lang_det.cc
    internal/compact_lang_det_hint_code.cc
    internal/compact_lang_det_impl.cc
    internal/debug.cc
    internal/fixunicodevalue.cc
    internal/generated_entities.cc
    internal/generated_language.cc
    internal/generated_ulscript.cc
    internal/getonescriptspan.cc
    internal/lang_script.cc
    internal/offsetmap.cc
    internal/scoreonescriptspan.cc
    internal/tote.cc
    internal/utf8statetable.cc
    internal/cld_generated_cjk_uni_prop_80.cc
    internal/cld2_generated_cjk_compatible.cc
    internal/cld_generated_cjk_delta_bi_4.cc
    internal/generated_distinct_bi_0.cc
    internal/cld2_generated_quadchrome_2.cc
    internal/cld2_generated_deltaoctachrome.cc
    internal/cld2_generated_distinctoctachrome.cc
    internal/cld_generated_score_quad_octa_2.cc
)
list(TRANSFORM CLD2_SOURCES PREPEND ${CLD2_DIR}/)

add_library(cld2 STATIC ${CLD2_SOURCES})
set_target_properties(cld2 PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_compile_options(cld2 PRIVATE -Wno-narrowing)
target_include_directories(cld2 PUBLIC ${CLD2_DIR}/public ${CLD2_DIR}/internal)
