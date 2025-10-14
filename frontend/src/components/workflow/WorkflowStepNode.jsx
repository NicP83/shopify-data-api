import { Handle, Position } from 'reactflow'

function WorkflowStepNode({ data, selected }) {
  const getStepTypeColor = (stepType) => {
    switch (stepType) {
      case 'AGENT_EXECUTION':
        return 'bg-blue-500'
      case 'CONDITION':
        return 'bg-yellow-500'
      case 'APPROVAL':
        return 'bg-purple-500'
      case 'PARALLEL':
        return 'bg-green-500'
      default:
        return 'bg-gray-500'
    }
  }

  const getStepTypeIcon = (stepType) => {
    switch (stepType) {
      case 'AGENT_EXECUTION':
        return '🤖'
      case 'CONDITION':
        return '🔀'
      case 'APPROVAL':
        return '✋'
      case 'PARALLEL':
        return '⚡'
      default:
        return '📦'
    }
  }

  return (
    <div
      className={`px-4 py-3 shadow-lg rounded-lg border-2 bg-white min-w-[200px] ${
        selected ? 'border-primary-500' : 'border-gray-300'
      }`}
    >
      <Handle type="target" position={Position.Top} className="w-3 h-3" />

      <div className="flex items-center gap-2 mb-2">
        <div className={`text-2xl ${getStepTypeColor(data.stepType)} w-10 h-10 rounded-full flex items-center justify-center`}>
          {getStepTypeIcon(data.stepType)}
        </div>
        <div className="flex-1">
          <div className="font-semibold text-gray-900 text-sm">
            {data.name || 'Untitled Step'}
          </div>
          <div className="text-xs text-gray-500">{data.stepType}</div>
        </div>
      </div>

      {data.agent && (
        <div className="text-xs text-gray-600 mb-1">
          <span className="font-medium">Agent:</span> {data.agent.name}
        </div>
      )}

      {data.conditionExpression && (
        <div className="text-xs text-gray-600 mb-1">
          <span className="font-medium">Condition:</span> {data.conditionExpression}
        </div>
      )}

      {data.outputVariable && (
        <div className="text-xs text-gray-500">
          <span className="font-medium">Output:</span> ${data.outputVariable}
        </div>
      )}

      <Handle type="source" position={Position.Bottom} className="w-3 h-3" />
    </div>
  )
}

export default WorkflowStepNode
