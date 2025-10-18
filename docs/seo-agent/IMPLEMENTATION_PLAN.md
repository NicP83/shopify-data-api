# SEO Agent - Complete Implementation Plan

**Document Version:** 1.0
**Created:** 2025-10-18
**Status:** Phase 1 - In Progress

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Phase 1: Navigation Reorganization](#phase-1-navigation-reorganization)
4. [Phase 2: SEO Agent Page Structure](#phase-2-seo-agent-page-structure)
5. [Phase 3: Chat Interface](#phase-3-chat-interface)
6. [Phase 4: Settings Panel](#phase-4-settings-panel)
7. [Phase 5: Backend Integration](#phase-5-backend-integration)
8. [Phase 6: Advanced Features](#phase-6-advanced-features)
9. [Technical Specifications](#technical-specifications)
10. [Testing Strategy](#testing-strategy)

---

## Project Overview

### Background

The SEO Agent is a hybrid tool that combines a conversational chat interface with configurable settings, allowing users to leverage AI for SEO-related tasks while having full control over which tools and agents are available, and how the AI behaves.

### Goals

- **Simplify UI**: Move workflow-related features into a "Temp Dev" section
- **Hybrid Interface**: Combine chat (similar to AI Chat Agent) with settings panel
- **Flexibility**: Allow users to select which tools and agents the SEO Agent can use
- **Configurability**: Provide full LLM settings control
- **Orchestration**: Main prompt manages the entire process, calling tools and agents as needed

### User Requirements (Original Request)

> "Let's do a tool that can be executed and some settings. Settings are: access to tools and agents, LLM options and main prompt. The main prompt will call various tools and manage the whole process. The interface will be a chat. It is important the use of agents within this chat. The settings tab will have all the typical AI settings that we already have on the settings tab of this system."

---

## Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────┐
│                    SEO Agent Page                        │
├──────────────────────────┬──────────────────────────────┤
│                          │                              │
│    Chat Interface        │     Settings Panel           │
│    (Left 65%)            │     (Right 35%)              │
│                          │                              │
│  - Message History       │  - Tool Selection            │
│  - User Input            │  - Agent Selection           │
│  - AI Responses          │  - LLM Configuration         │
│  - Streaming             │  - Main Orchestration Prompt │
│  - Tool/Agent Calls      │  - Save/Load Configs         │
│                          │                              │
└──────────────────────────┴──────────────────────────────┘
```

### Technology Stack

**Frontend:**
- React 18
- React Router v6
- Tailwind CSS
- Existing component library (ChatMessageBubble, ChatInput)

**Backend (Future):**
- Spring Boot
- Claude API (Anthropic)
- Existing Agent/Tool framework
- WebSocket for streaming (optional)

---

## Phase 1: Navigation Reorganization

**Status:** In Progress
**Priority:** P0 (Immediate)

### Objectives

1. Clean up main navigation by grouping workflow features
2. Create dropdown menu for "Temp Dev" items
3. Add new top-level "SEO Agent" menu item

### Implementation Details

#### 1.1 Navigation.jsx Changes

**Current Structure:**
```jsx
const navItems = [
  { path: '/', label: 'Dashboard', icon: '🏠' },
  { path: '/products', label: 'Product Search', icon: '🔍' },
  { path: '/chat', label: 'AI Chat Agent', icon: '💬' },
  { path: '/fulfillment', label: 'Orders to Fulfill', icon: '📦' },
  { path: '/agents', label: 'Agents', icon: '🤖' },
  { path: '/workflows', label: 'Workflows', icon: '🔄' },
  { path: '/workflow-gallery', label: 'Workflow Gallery', icon: '🎨' },
  { path: '/executions', label: 'Executions', icon: '📈' },
  { path: '/approvals', label: 'Approvals', icon: '✅', badge: approvalCount },
  { path: '/settings', label: 'Settings', icon: '⚙️' },
  { path: '/analytics', label: 'Analytics', icon: '📊' },
  { path: '/market-intel', label: 'Market Intel', icon: '💰' },
]
```

**New Structure:**
```jsx
const regularNavItems = [
  { path: '/', label: 'Dashboard', icon: '🏠' },
  { path: '/products', label: 'Product Search', icon: '🔍' },
  { path: '/chat', label: 'AI Chat Agent', icon: '💬' },
  { path: '/fulfillment', label: 'Orders to Fulfill', icon: '📦' },
  { path: '/agents', label: 'Agents', icon: '🤖' },
  // TEMP DEV DROPDOWN HERE
  { path: '/seo-agent', label: 'SEO Agent', icon: '🎯' }, // NEW
  { path: '/settings', label: 'Settings', icon: '⚙️' },
  { path: '/analytics', label: 'Analytics', icon: '📊' },
  { path: '/market-intel', label: 'Market Intel', icon: '💰' },
]

const tempDevItems = [
  { path: '/workflows', label: 'Workflows', icon: '🔄' },
  { path: '/workflow-gallery', label: 'Workflow Gallery', icon: '🎨' },
  { path: '/executions', label: 'Executions', icon: '📈' },
  { path: '/approvals', label: 'Approvals', icon: '✅', badge: approvalCount },
]
```

#### 1.2 Dropdown Component

**Features:**
- Hover or click to expand
- Show chevron icon (▼/▲) to indicate state
- Highlight when any child route is active
- Display badge count for Approvals
- Smooth animation on open/close
- Click outside to close

**Code Approach:**
```jsx
const [dropdownOpen, setDropdownOpen] = useState(false)
const dropdownRef = useRef(null)

// Click outside handler
useEffect(() => {
  function handleClickOutside(event) {
    if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
      setDropdownOpen(false)
    }
  }
  document.addEventListener('mousedown', handleClickOutside)
  return () => document.removeEventListener('mousedown', handleClickOutside)
}, [])

// Check if any temp dev item is active
const isTempDevActive = () => {
  return tempDevItems.some(item => isActive(item.path))
}
```

#### 1.3 Active State Logic

Update `isActive()` function to handle dropdown items:
```jsx
const isActive = (path) => {
  if (path === '/workflows') {
    return location.pathname.startsWith('/workflows')
  }
  if (path === '/agents') {
    return location.pathname.startsWith('/agents')
  }
  if (path === '/executions') {
    return location.pathname.startsWith('/executions')
  }
  if (path === '/approvals') {
    return location.pathname.startsWith('/approvals')
  }
  if (path === '/seo-agent') {
    return location.pathname.startsWith('/seo-agent')
  }
  return location.pathname === path
}
```

#### 1.4 Files Modified

- `frontend/src/components/Navigation.jsx`

---

## Phase 2: SEO Agent Page Structure

**Status:** Planned
**Priority:** P0 (Immediate)

### Objectives

1. Create placeholder SEO Agent page
2. Set up route in App.jsx
3. Implement basic two-panel layout
4. Prepare structure for future implementation

### Implementation Details

#### 2.1 App.jsx Route Addition

```jsx
import SeoAgent from './pages/SeoAgent'

// In Routes:
<Route path="/seo-agent" element={<SeoAgent />} />
```

#### 2.2 SeoAgent.jsx Structure

**File:** `frontend/src/pages/SeoAgent.jsx`

**Initial Implementation:**
```jsx
import { useState } from 'react'

function SeoAgent() {
  const [settingsOpen, setSettingsOpen] = useState(true)

  return (
    <div className="max-w-full mx-auto">
      {/* Header */}
      <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 mb-2">
              🎯 SEO Agent
            </h1>
            <p className="text-gray-600">
              AI-powered SEO assistant with configurable tools and agents
            </p>
          </div>
          <button
            onClick={() => setSettingsOpen(!settingsOpen)}
            className="bg-gray-200 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-300 transition-colors"
          >
            {settingsOpen ? 'Hide Settings' : 'Show Settings'}
          </button>
        </div>
      </div>

      {/* Main Content - Two Panel Layout */}
      <div className="flex gap-6" style={{ height: 'calc(100vh - 280px)' }}>
        {/* Chat Panel - Left */}
        <div className={`bg-white rounded-lg shadow-sm ${settingsOpen ? 'w-2/3' : 'w-full'} transition-all`}>
          <div className="p-6 h-full flex flex-col">
            <div className="flex-1 flex items-center justify-center text-gray-400">
              <div className="text-center">
                <p className="text-4xl mb-4">💬</p>
                <p className="text-lg">Chat interface coming soon</p>
                <p className="text-sm mt-2">This will be similar to the AI Chat Agent</p>
              </div>
            </div>
          </div>
        </div>

        {/* Settings Panel - Right */}
        {settingsOpen && (
          <div className="w-1/3 bg-white rounded-lg shadow-sm">
            <div className="p-6">
              <h2 className="text-lg font-bold text-gray-900 mb-4">Settings</h2>

              {/* Settings content placeholder */}
              <div className="space-y-6 text-sm text-gray-600">
                <div>
                  <p className="font-semibold mb-2">Tool & Agent Selection</p>
                  <p>Configure which tools and agents are available</p>
                </div>

                <div>
                  <p className="font-semibold mb-2">LLM Configuration</p>
                  <p>Model, temperature, max tokens, and other AI settings</p>
                </div>

                <div>
                  <p className="font-semibold mb-2">Main Orchestration Prompt</p>
                  <p>The system prompt that manages tool/agent usage</p>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

export default SeoAgent
```

#### 2.3 Files Created

- `frontend/src/pages/SeoAgent.jsx`

#### 2.4 Files Modified

- `frontend/src/App.jsx`

---

## Phase 3: Chat Interface

**Status:** Planned
**Priority:** P1 (Next)

### Objectives

1. Implement chat UI similar to existing ChatAgent.jsx
2. Add message history management
3. Implement streaming responses (optional)
4. Show tool/agent invocations in chat
5. Handle errors gracefully

### Implementation Details

#### 3.1 State Management

```jsx
const [messages, setMessages] = useState([])
const [loading, setLoading] = useState(false)
const [error, setError] = useState(null)
const messagesEndRef = useRef(null)
```

#### 3.2 Message Structure

```javascript
{
  role: 'user' | 'assistant' | 'system' | 'tool',
  content: string,
  timestamp: number,
  toolCall?: {
    name: string,
    arguments: object,
    result: any
  },
  agentCall?: {
    name: string,
    status: 'running' | 'completed' | 'failed',
    result: any
  }
}
```

#### 3.3 Components to Reuse

- `ChatMessageBubble` - Display messages
- `ChatInput` - User input field

#### 3.4 New Components Needed

- `ToolCallDisplay` - Show tool invocations inline
- `AgentCallDisplay` - Show agent execution status
- `StreamingIndicator` - Show AI typing indicator

#### 3.5 API Integration Points

```javascript
// Send message to SEO Agent
POST /api/seo-agent/chat
{
  message: string,
  conversationHistory: Message[],
  settings: {
    availableTools: string[],
    availableAgents: string[],
    llmConfig: LLMConfig,
    orchestrationPrompt: string
  }
}

// Response
{
  success: boolean,
  data: {
    content: string,
    timestamp: number,
    toolCalls: ToolCall[],
    agentCalls: AgentCall[]
  }
}
```

---

## Phase 4: Settings Panel

**Status:** Planned
**Priority:** P1 (Next)

### Objectives

1. Tool/Agent multi-select dropdowns
2. LLM configuration UI
3. Orchestration prompt editor
4. Save/Load configuration profiles
5. Validate settings before use

### Implementation Details

#### 4.1 Tool Selection Component

```jsx
<div className="space-y-2">
  <label className="block text-sm font-medium text-gray-700">
    Available Tools
  </label>
  <select
    multiple
    className="w-full border rounded-lg p-2"
    value={selectedTools}
    onChange={(e) => setSelectedTools(Array.from(e.target.selectedOptions, o => o.value))}
  >
    {allTools.map(tool => (
      <option key={tool.id} value={tool.id}>
        {tool.name}
      </option>
    ))}
  </select>
  <p className="text-xs text-gray-500">
    Hold Ctrl/Cmd to select multiple tools
  </p>
</div>
```

#### 4.2 Agent Selection Component

Similar to Tool Selection, but for agents:

```jsx
<div className="space-y-2">
  <label className="block text-sm font-medium text-gray-700">
    Available Agents
  </label>
  <select
    multiple
    className="w-full border rounded-lg p-2"
    value={selectedAgents}
    onChange={(e) => setSelectedAgents(Array.from(e.target.selectedOptions, o => o.value))}
  >
    {allAgents.map(agent => (
      <option key={agent.id} value={agent.id}>
        {agent.name}
      </option>
    ))}
  </select>
  <p className="text-xs text-gray-500">
    Agents can be invoked during conversation
  </p>
</div>
```

#### 4.3 LLM Configuration

Reuse existing Settings page components:

```jsx
// Model Selection
<select className="w-full border rounded-lg p-2" value={model} onChange={...}>
  <option value="claude-3-5-sonnet-20241022">Claude 3.5 Sonnet</option>
  <option value="claude-3-opus-20240229">Claude 3 Opus</option>
  <option value="claude-3-haiku-20240307">Claude 3 Haiku</option>
</select>

// Temperature
<input
  type="range"
  min="0"
  max="1"
  step="0.1"
  value={temperature}
  onChange={...}
/>

// Max Tokens
<input
  type="number"
  min="1"
  max="200000"
  value={maxTokens}
  onChange={...}
/>

// Top P
<input
  type="range"
  min="0"
  max="1"
  step="0.1"
  value={topP}
  onChange={...}
/>
```

#### 4.4 Orchestration Prompt Editor

```jsx
<div className="space-y-2">
  <label className="block text-sm font-medium text-gray-700">
    Main Orchestration Prompt
  </label>
  <textarea
    rows={10}
    className="w-full border rounded-lg p-3 font-mono text-sm"
    value={orchestrationPrompt}
    onChange={(e) => setOrchestrationPrompt(e.target.value)}
    placeholder="You are an SEO expert assistant. You have access to tools and agents to help users with SEO tasks..."
  />
  <p className="text-xs text-gray-500">
    This prompt manages how the AI uses available tools and agents
  </p>
</div>
```

#### 4.5 Configuration Persistence

**LocalStorage (Phase 4a):**
```javascript
// Save
localStorage.setItem('seo-agent-config', JSON.stringify({
  selectedTools,
  selectedAgents,
  llmConfig,
  orchestrationPrompt
}))

// Load
const savedConfig = JSON.parse(localStorage.getItem('seo-agent-config') || '{}')
```

**Backend API (Phase 4b - Future):**
```javascript
// Save configuration
POST /api/seo-agent/configs
{
  name: string,
  description: string,
  config: ConfigObject
}

// List configurations
GET /api/seo-agent/configs

// Load configuration
GET /api/seo-agent/configs/:id
```

---

## Phase 5: Backend Integration

**Status:** Planned
**Priority:** P2 (Future)

### Objectives

1. Create SEO Agent controller
2. Implement chat message processing
3. Tool/Agent orchestration logic
4. Session management
5. Configuration storage

### Implementation Details

#### 5.1 New Backend Files

**Controller:**
```java
@RestController
@RequestMapping("/api/seo-agent")
public class SeoAgentController {

    @PostMapping("/chat")
    public ResponseEntity<?> processMessage(@RequestBody ChatRequest request) {
        // Process message with selected tools/agents
    }

    @GetMapping("/tools")
    public ResponseEntity<?> getAvailableTools() {
        // Return list of all tools
    }

    @GetMapping("/agents")
    public ResponseEntity<?> getAvailableAgents() {
        // Return list of all agents
    }

    @PostMapping("/configs")
    public ResponseEntity<?> saveConfiguration(@RequestBody ConfigRequest request) {
        // Save configuration
    }

    @GetMapping("/configs")
    public ResponseEntity<?> getConfigurations() {
        // List saved configurations
    }
}
```

**Service:**
```java
@Service
public class SeoAgentService {

    @Autowired
    private AnthropicService anthropicService;

    @Autowired
    private ToolService toolService;

    @Autowired
    private AgentService agentService;

    public ChatResponse processMessage(
        String message,
        List<Message> history,
        List<String> selectedTools,
        List<String> selectedAgents,
        LLMConfig llmConfig,
        String orchestrationPrompt
    ) {
        // 1. Build conversation with orchestration prompt
        // 2. Add available tools to context
        // 3. Send to Claude API
        // 4. Handle tool/agent calls
        // 5. Return response
    }

    private void executeToolCall(ToolCall toolCall) {
        // Execute tool and return result
    }

    private void executeAgentCall(AgentCall agentCall) {
        // Execute agent workflow and return result
    }
}
```

#### 5.2 Database Schema (Optional)

```sql
CREATE TABLE seo_agent_configs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    config_json TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE seo_agent_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) UNIQUE NOT NULL,
    config_id BIGINT REFERENCES seo_agent_configs(id),
    messages_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 5.3 Tool/Agent Orchestration

The main orchestration prompt should instruct Claude on:

1. When to use tools vs agents
2. How to chain multiple tool/agent calls
3. Error handling
4. Response formatting

**Example Orchestration Prompt:**

```
You are an SEO expert assistant with access to specialized tools and agents.

AVAILABLE TOOLS:
{tool_list}

AVAILABLE AGENTS:
{agent_list}

GUIDELINES:
1. Use tools for simple, single-purpose tasks (e.g., keyword research, SERP analysis)
2. Use agents for complex, multi-step workflows (e.g., content optimization, site audit)
3. You can chain multiple tool/agent calls to accomplish complex tasks
4. Always explain what you're doing before calling a tool or agent
5. Present results in a clear, actionable format

USER TASK:
{user_message}

Process the user's request using available tools and agents as needed.
```

---

## Phase 6: Advanced Features

**Status:** Planned
**Priority:** P3 (Future)

### Potential Enhancements

1. **Streaming Responses**: Real-time token streaming via WebSocket
2. **Multi-Session Support**: Save and resume conversations
3. **Configuration Templates**: Pre-built configs for common SEO tasks
4. **Usage Analytics**: Track tool/agent invocations
5. **Collaboration**: Share configurations with team
6. **Export/Import**: Export chat logs and configurations
7. **Custom Tools**: Allow users to add custom tools
8. **Workflow Conversion**: Convert successful chat sessions into reusable workflows

---

## Technical Specifications

### Frontend Stack

- **Framework**: React 18.2+
- **Routing**: React Router v6
- **Styling**: Tailwind CSS
- **State Management**: React useState/useEffect
- **HTTP Client**: Axios (via existing api.js)

### Backend Stack

- **Framework**: Spring Boot 3.x
- **Language**: Java 17+
- **Database**: PostgreSQL (Railway)
- **AI Provider**: Anthropic Claude API
- **Build Tool**: Maven

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/seo-agent/chat` | Send message to SEO Agent |
| GET | `/api/seo-agent/tools` | List available tools |
| GET | `/api/seo-agent/agents` | List available agents |
| POST | `/api/seo-agent/configs` | Save configuration |
| GET | `/api/seo-agent/configs` | List saved configurations |
| GET | `/api/seo-agent/configs/:id` | Load specific configuration |
| DELETE | `/api/seo-agent/configs/:id` | Delete configuration |

### Data Models

**SeoAgentConfig:**
```javascript
{
  id: number,
  name: string,
  description: string,
  selectedTools: string[],
  selectedAgents: string[],
  llmConfig: {
    model: string,
    temperature: number,
    maxTokens: number,
    topP: number,
    frequencyPenalty: number,
    presencePenalty: number
  },
  orchestrationPrompt: string,
  createdAt: timestamp,
  updatedAt: timestamp
}
```

**ChatMessage:**
```javascript
{
  role: 'user' | 'assistant' | 'system' | 'tool',
  content: string,
  timestamp: number,
  toolCalls?: ToolCall[],
  agentCalls?: AgentCall[]
}
```

---

## Testing Strategy

### Phase 1 Testing

- ✅ Verify dropdown menu appears and works
- ✅ Verify all 4 items in dropdown
- ✅ Verify SEO Agent menu item appears
- ✅ Verify all routes still work
- ✅ Verify approval badge shows in dropdown
- ✅ Verify active state highlighting

### Phase 2 Testing

- ✅ Verify SEO Agent page loads
- ✅ Verify two-panel layout
- ✅ Verify settings toggle works
- ✅ Verify responsive design

### Phase 3 Testing

- Chat message sending/receiving
- Message history persistence
- Error handling
- Tool/Agent call display
- Streaming (if implemented)

### Phase 4 Testing

- Tool selection save/load
- Agent selection save/load
- LLM config validation
- Prompt editor functionality
- Configuration persistence

### Phase 5 Testing

- API endpoint functionality
- Tool orchestration logic
- Agent orchestration logic
- Error handling
- Performance under load

---

## Implementation Timeline

### Week 1
- ✅ Phase 1: Navigation Reorganization
- ✅ Phase 2: SEO Agent Page Structure
- Documentation

### Week 2-3
- Phase 3: Chat Interface Implementation
- Phase 4: Settings Panel Implementation
- Integration testing

### Week 4-5
- Phase 5: Backend Integration
- End-to-end testing
- Bug fixes

### Week 6+
- Phase 6: Advanced Features (as needed)
- Performance optimization
- User feedback incorporation

---

## Success Criteria

### Phase 1
- ✅ Navigation cleanly organized
- ✅ Dropdown menu functional
- ✅ SEO Agent accessible

### Phase 2
- ✅ SEO Agent page loads without errors
- ✅ Layout responsive and functional

### Phase 3
- Users can have natural conversations
- Tool/Agent calls visible in chat
- Error handling graceful

### Phase 4
- Users can configure all settings
- Settings persist across sessions
- Validation prevents invalid configs

### Phase 5
- Backend processes messages correctly
- Tools and agents execute as expected
- Performance acceptable (<2s response time)

---

## References

### Existing Code to Reference

- **Chat Interface**: `frontend/src/pages/ChatAgent.jsx`
- **Settings Panel**: `frontend/src/pages/Settings.jsx`
- **Agent Management**: `src/main/java/com/shopify/api/service/agent/`
- **Tool Integration**: `src/main/java/com/shopify/api/service/AnthropicService.java`

### Related Documentation

- `docs/multi-agent/ARCHITECTURE.md` - Multi-agent system design
- `docs/API_REFERENCE.md` - API endpoint documentation
- `docs/ADDING_FUNCTIONS.md` - Adding new tool functions

---

## Notes and Considerations

### Design Decisions

1. **Why Two-Panel Layout?**
   - Allows simultaneous view of chat and settings
   - Settings can be adjusted mid-conversation
   - Familiar pattern from other AI tools

2. **Why Dropdown for Temp Dev?**
   - Reduces navigation clutter
   - Groups related features
   - Temporary solution during development

3. **Why Separate from AI Chat Agent?**
   - SEO Agent has specific configuration needs
   - Different use case (SEO-focused vs general chat)
   - Allows specialized tool/agent selection

### Future Considerations

- **Mobile Responsive**: Two-panel layout may need to stack on mobile
- **Performance**: Monitor API response times with multiple tool/agent calls
- **Security**: Validate tool/agent access permissions
- **Rate Limiting**: Consider API usage limits
- **Caching**: Cache tool/agent lists for performance

---

## Changelog

### Version 1.0 (2025-10-18)
- Initial plan created
- All phases documented
- Technical specifications defined
- Implementation timeline outlined

---

**Document Owner**: Development Team
**Last Updated**: 2025-10-18
**Status**: Living Document - Update as implementation progresses
