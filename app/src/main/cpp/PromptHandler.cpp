// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
#include "PromptHandler.hpp"
#include "GenieWrapper.hpp"

using namespace AppUtils;

// Microsoft Phi prompt format
constexpr const std::string_view c_system_prompt = "<|system|>\nYou are a helpful assistant. Be helpful but brief.<|end|>\n";
constexpr const std::string_view c_prompt_prefix = "<|user|>";
constexpr const std::string_view c_end_of_prompt = "\n<|end|>\n";
constexpr const std::string_view c_assistant_header = "<|assistant|>\n";

PromptHandler::PromptHandler()
    : m_is_first_prompt(true)
{
}

std::string PromptHandler::GetPromptWithTag(const std::string& user_prompt)
{
    // Microsoft Phi prompt format
    if (m_is_first_prompt)
    {
        m_is_first_prompt = false;
        return std::string(c_system_prompt) + c_prompt_prefix.data() + user_prompt + c_end_of_prompt.data() + c_assistant_header.data();
    }
    return std::string(c_prompt_prefix) + user_prompt + c_end_of_prompt.data() + c_assistant_header.data();
}
